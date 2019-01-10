package uk.gov.hmcts.reform.bulkscan.orchestrator.services;

import com.google.common.base.Strings;
import com.microsoft.azure.servicebus.ExceptionPhase;
import com.microsoft.azure.servicebus.IMessage;
import com.microsoft.azure.servicebus.IMessageHandler;
import com.microsoft.azure.servicebus.primitives.ServiceBusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CaseRetriever;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.events.EventPublisher;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.events.EventPublisherContainer;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.IMessageOperations;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.exceptions.InvalidMessageException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.EnvelopeEventProcessor.MessageProcessingResultType.POTENTIALLY_RECOVERABLE_FAILURE;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.EnvelopeEventProcessor.MessageProcessingResultType.SUCCESS;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.EnvelopeEventProcessor.MessageProcessingResultType.UNRECOVERABLE_FAILURE;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.EnvelopeParser.parse;

@Service
public class EnvelopeEventProcessor implements IMessageHandler {

    private static final Logger log = LoggerFactory.getLogger(EnvelopeEventProcessor.class);

    private final CaseRetriever caseRetriever;
    private final EventPublisherContainer eventPublisherContainer;
    private final IMessageOperations messageOperations;

    public EnvelopeEventProcessor(
        CaseRetriever caseRetriever,
        EventPublisherContainer eventPublisherContainer,
        IMessageOperations messageOperations
    ) {
        this.caseRetriever = caseRetriever;
        this.eventPublisherContainer = eventPublisherContainer;
        this.messageOperations = messageOperations;
    }

    @Override
    public CompletableFuture<Void> onMessageAsync(IMessage message) {
        /*
         * NOTE: this is done here instead of offloading to the forkJoin pool "CompletableFuture.runAsync()"
         * because we probably should think about a threading model before doing this.
         * Maybe consider using Netflix's RxJava too (much simpler than CompletableFuture).
         */
        CompletableFuture<Void> completableFuture = new CompletableFuture<>();
        try {
            MessageProcessingResult result = process(message);
            tryFinaliseProcessedMessage(message, result);

            completableFuture.complete(null);
        } catch (Throwable t) {
            completableFuture.completeExceptionally(t);
        }
        return completableFuture;
    }

    private MessageProcessingResult process(IMessage message) {
        log.info("Started processing message with ID {}", message.getMessageId());
        try {
            Envelope envelope = parse(message.getBody());
            log.info(
                "Parsed message with ID {}. Envelope ID: {}, File name: {}. Jurisdiction: {}",
                message.getMessageId(),
                envelope.id,
                envelope.zipFileName,
                envelope.jurisdiction
            );

            Supplier<CaseDetails> caseRetrieval = () -> Strings.isNullOrEmpty(envelope.caseRef)
                ? null
                : caseRetriever.retrieve(envelope.jurisdiction, envelope.caseRef);
            EventPublisher eventPublisher = eventPublisherContainer.getPublisher(
                envelope.classification,
                caseRetrieval
            );

            eventPublisher.publish(envelope);
            log.info("Processed message with ID {}", message.getMessageId());
            return new MessageProcessingResult(SUCCESS);
        } catch (InvalidMessageException ex) {
            log.error("Rejected message with ID {}, because it's invalid", message.getMessageId(), ex);
            return new MessageProcessingResult(UNRECOVERABLE_FAILURE, ex);
        } catch (Exception ex) {
            log.error("Failed to process message with ID {}", message.getMessageId(), ex);
            return new MessageProcessingResult(POTENTIALLY_RECOVERABLE_FAILURE);
        }
    }

    private void tryFinaliseProcessedMessage(IMessage message, MessageProcessingResult processingResult) {
        try {
            finaliseProcessedMessage(message, processingResult);
        } catch (Exception ex) {
            log.error(
                "Failed to manage processed message with ID {}. Processing result: {}",
                message.getMessageId(),
                processingResult,
                ex
            );
        }
    }

    private void finaliseProcessedMessage(
        IMessage message,
        MessageProcessingResult processingResult
    ) throws InterruptedException, ServiceBusException {

        switch (processingResult.resultType) {
            case SUCCESS:
                messageOperations.complete(message.getLockToken());
                log.info("Message with ID {} has been completed", message.getMessageId());
                break;
            case UNRECOVERABLE_FAILURE:
                messageOperations.deadLetter(
                    message.getLockToken(),
                    "Message processing error",
                    processingResult.exception.getMessage()
                );

                log.info("Message with ID {} has been dead-lettered", message.getMessageId());
                break;
            case POTENTIALLY_RECOVERABLE_FAILURE:
                // do nothing - let the message lock expire
                log.info("Allowing message with ID {} to return to queue", message.getMessageId());
                break;
            default:
                throw new MessageProcessingException(
                    "Unknown message processing result type: " + processingResult.resultType
                );
        }
    }

    @Override
    public void notifyException(Throwable exception, ExceptionPhase phase) {
        log.error("Error while handling message at stage: " + phase, exception);
    }

    class MessageProcessingResult {
        public final MessageProcessingResultType resultType;
        public final Exception exception;

        public MessageProcessingResult(MessageProcessingResultType resultType) {
            this(resultType, null);
        }

        public MessageProcessingResult(MessageProcessingResultType resultType, Exception exception) {
            this.resultType = resultType;
            this.exception = exception;
        }
    }

    enum MessageProcessingResultType {
        SUCCESS,
        UNRECOVERABLE_FAILURE,
        POTENTIALLY_RECOVERABLE_FAILURE
    }
}
