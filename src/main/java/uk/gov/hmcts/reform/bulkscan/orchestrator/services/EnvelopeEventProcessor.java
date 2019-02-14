package uk.gov.hmcts.reform.bulkscan.orchestrator.services;

import com.google.common.base.Strings;
import com.microsoft.azure.servicebus.ExceptionPhase;
import com.microsoft.azure.servicebus.IMessage;
import com.microsoft.azure.servicebus.IMessageHandler;
import com.microsoft.azure.servicebus.primitives.ServiceBusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CcdApi;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.events.EventPublisher;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.events.EventPublisherContainer;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.IMessageOperations;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.IProcessedEnvelopeNotifier;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.NotificationSendingException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.exceptions.InvalidMessageException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.EnvelopeEventProcessor.MessageProcessingResultType.POTENTIALLY_RECOVERABLE_FAILURE;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.EnvelopeEventProcessor.MessageProcessingResultType.SUCCESS;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.EnvelopeEventProcessor.MessageProcessingResultType.UNRECOVERABLE_FAILURE;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.EnvelopeParser.parse;

@Service
public class EnvelopeEventProcessor implements IMessageHandler {

    private static final Logger log = LoggerFactory.getLogger(EnvelopeEventProcessor.class);

    private final BiFunction<String, String, CaseDetails> caseRetriever;
    private final EventPublisherContainer eventPublisherContainer;
    private final IProcessedEnvelopeNotifier processedEnvelopeNotifier;
    private final IMessageOperations messageOperations;

    public EnvelopeEventProcessor(
        CcdApi ccdApi,
        EventPublisherContainer eventPublisherContainer,
        IProcessedEnvelopeNotifier processedEnvelopeNotifier,
        IMessageOperations messageOperations
    ) {
        this.caseRetriever = ccdApi::getCaseOptionally;
        this.eventPublisherContainer = eventPublisherContainer;
        this.processedEnvelopeNotifier = processedEnvelopeNotifier;
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

        Envelope envelope = null;

        try {
            envelope = parse(message.getBody());

            logMessageParsed(message, envelope);

            EventPublisher eventPublisher = eventPublisherContainer.getPublisher(
                envelope.classification,
                getCase(envelope)
            );

            eventPublisher.publish(envelope);
            processedEnvelopeNotifier.notify(envelope.id);
            log.info("Processed message with ID {}. File name: {}", message.getMessageId(), envelope.zipFileName);
            return new MessageProcessingResult(SUCCESS);
        } catch (InvalidMessageException ex) {
            log.error("Rejected message with ID {}, because it's invalid", message.getMessageId(), ex);
            return new MessageProcessingResult(UNRECOVERABLE_FAILURE, ex);
        } catch (NotificationSendingException ex) {
            logMessageProcessingError(message, envelope, ex);

            // CCD changes have been made, so it's better to dead-letter the message and
            // not repeat them, at least until CCD operations become idempotent
            return new MessageProcessingResult(UNRECOVERABLE_FAILURE, ex);
        } catch (Exception ex) {
            logMessageProcessingError(message, envelope, ex);
            return new MessageProcessingResult(POTENTIALLY_RECOVERABLE_FAILURE);
        }
    }

    private void tryFinaliseProcessedMessage(IMessage message, MessageProcessingResult processingResult) {
        try {
            finaliseProcessedMessage(message, processingResult);
        } catch (InterruptedException ex) {
            logMessageFinaliseError(message, processingResult.resultType, ex);
            Thread.currentThread().interrupt();
        } catch (Exception ex) {
            logMessageFinaliseError(message, processingResult.resultType, ex);
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
                log.info(
                    "Allowing message with ID {} to return to queue (delivery attempt {})",
                    message.getMessageId(),
                    message.getDeliveryCount() + 1
                );
                break;
            default:
                throw new MessageProcessingException(
                    "Unknown message processing result type: " + processingResult.resultType
                );
        }
    }

    private void logMessageFinaliseError(
        IMessage message,
        MessageProcessingResultType processingResultType,
        Exception ex
    ) {
        log.error(
            "Failed to manage processed message with ID {}. Processing result: {}",
            message.getMessageId(),
            processingResultType,
            ex
        );
    }

    @Override
    public void notifyException(Throwable exception, ExceptionPhase phase) {
        log.error("Error while handling message at stage: " + phase, exception);
    }

    private Supplier<CaseDetails> getCase(final Envelope envelope) {
        return () -> Strings.isNullOrEmpty(envelope.caseRef)
            ? null
            : caseRetriever.apply(envelope.caseRef, envelope.jurisdiction);
    }

    private void logMessageParsed(IMessage message, Envelope envelope) {
        log.info(
            "Parsed message. ID: {}, Envelope ID: {}, File name: {}, Jurisdiction: {}, Classification: {}, Case: {}",
            message.getMessageId(),
            envelope.id,
            envelope.zipFileName,
            envelope.jurisdiction,
            envelope.classification,
            envelope.caseRef
        );
    }

    private void logMessageProcessingError(IMessage message, Envelope envelope, Exception exception) {
        String baseMessage = String.format("Failed to process message with ID %s.", message.getMessageId());

        String fullMessage = envelope != null
            ? baseMessage + String.format(" Envelope ID: %s, File name: %s", envelope.id, envelope.zipFileName)
            : baseMessage;

        log.error(fullMessage, exception);
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
