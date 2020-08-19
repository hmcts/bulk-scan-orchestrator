package uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes;

import com.google.common.collect.ImmutableMap;
import com.microsoft.azure.servicebus.IMessage;
import com.microsoft.azure.servicebus.IMessageReceiver;
import com.microsoft.azure.servicebus.primitives.ServiceBusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.bulkscan.orchestrator.logging.AppInsights;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.events.EnvelopeHandler;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.MessageBodyRetriever;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.handler.MessageProcessingResult;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.handler.MessageProcessingResultType;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Envelope;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.processedenvelopes.EnvelopeProcessingResult;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.processedenvelopes.IProcessedEnvelopeNotifier;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.exceptions.InvalidMessageException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.exceptions.MessageProcessingException;

import java.time.Instant;
import java.util.Objects;

import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.EnvelopeParser.parse;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.handler.MessageProcessingResultType.POTENTIALLY_RECOVERABLE_FAILURE;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.handler.MessageProcessingResultType.SUCCESS;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.handler.MessageProcessingResultType.UNRECOVERABLE_FAILURE;

@Service
public class EnvelopeMessageProcessor {

    private static final Logger log = LoggerFactory.getLogger(EnvelopeMessageProcessor.class);

    public static final String HEARTBEAT_LABEL = "heartbeat";

    private final EnvelopeHandler envelopeHandler;
    private final IProcessedEnvelopeNotifier processedEnvelopeNotifier;
    private final IMessageReceiver messageReceiver;
    private final int maxDeliveryCount;
    private final AppInsights appInsights;

    public EnvelopeMessageProcessor(
        EnvelopeHandler envelopeHandler,
        IProcessedEnvelopeNotifier processedEnvelopeNotifier,
        IMessageReceiver messageReceiver,
        @Value("${azure.servicebus.envelopes.max-delivery-count}") int maxDeliveryCount,
        AppInsights appInsights
    ) {
        this.envelopeHandler = envelopeHandler;
        this.processedEnvelopeNotifier = processedEnvelopeNotifier;
        this.messageReceiver = messageReceiver;
        this.maxDeliveryCount = maxDeliveryCount;
        this.appInsights = appInsights;
    }

    /**
     * Reads and processes next message from the queue.
     *
     * @return false if there was no message to process. Otherwise true.
     */
    public boolean processNextMessage() throws ServiceBusException, InterruptedException {
        IMessage message = messageReceiver.receive();

        if (message != null) {
            log.info("Started processing message with ID {}", message.getMessageId());
            MessageProcessingResult result = process(message);
            tryFinaliseProcessedMessage(message, result);
        } else {
            log.info("No envelope messages left to process");
        }

        return message != null;
    }

    private MessageProcessingResult process(IMessage message) {
        if (Objects.equals(message.getLabel(), HEARTBEAT_LABEL)) {
            log.info("Heartbeat message received");
            return new MessageProcessingResult(SUCCESS);
        } else {
            Envelope envelope = null;

            try {
                envelope = parse(MessageBodyRetriever.getBinaryData(message.getMessageBody()));
                logMessageParsed(message, envelope);
                EnvelopeProcessingResult envelopeProcessingResult =
                    envelopeHandler.handleEnvelope(envelope, message.getDeliveryCount());
                processedEnvelopeNotifier.notify(
                    envelope.id,
                    envelopeProcessingResult.ccdId,
                    envelopeProcessingResult.envelopeCcdAction
                );
                log.info("Processed message with ID {}. File name: {}", message.getMessageId(), envelope.zipFileName);
                return new MessageProcessingResult(SUCCESS);
            } catch (InvalidMessageException ex) {
                log.error("Rejected message with ID {}, because it's invalid", message.getMessageId(), ex);
                return new MessageProcessingResult(UNRECOVERABLE_FAILURE, ex);
            } catch (Exception ex) {
                logMessageProcessingError(message, envelope, ex);
                return new MessageProcessingResult(POTENTIALLY_RECOVERABLE_FAILURE);
            }
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
                messageReceiver.complete(message.getLockToken());
                log.info("Message with ID {} has been completed", message.getMessageId());
                break;
            case UNRECOVERABLE_FAILURE:
                deadLetterTheMessage(
                    message,
                    "Message processing error",
                    processingResult.exception.getMessage()
                );

                break;
            case POTENTIALLY_RECOVERABLE_FAILURE:
                // starts from 0
                int deliveryCount = (int) message.getDeliveryCount() + 1;

                if (deliveryCount < maxDeliveryCount) {
                    // do nothing - let the message lock expire
                    log.info(
                        "Allowing message with ID {} to return to queue (delivery attempt {})",
                        message.getMessageId(),
                        deliveryCount
                    );
                } else {
                    deadLetterTheMessage(
                        message,
                        "Too many deliveries",
                        "Reached limit of message delivery count of " + deliveryCount
                    );
                }

                break;
            default:
                throw new MessageProcessingException(
                    "Unknown message processing result type: " + processingResult.resultType
                );
        }
    }

    private void deadLetterTheMessage(
        IMessage message,
        String reason,
        String description
    ) throws InterruptedException, ServiceBusException {
        messageReceiver.deadLetter(
            message.getLockToken(),
            reason,
            description,
            ImmutableMap.of("deadLetteredAt", Instant.now().toString())
        );

        log.info("Message with ID {} has been dead-lettered", message.getMessageId());
        // track used for alert
        appInsights.trackDeadLetteredMessage(message, "envelopes", reason, description);
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

    private void logMessageParsed(IMessage message, Envelope envelope) {
        log.info(
            "Parsed message. ID: {}, Envelope ID: {}, File name: {}, Jurisdiction: {}, Form type: {}, "
                + "Classification: {}, {}: {}",
            message.getMessageId(),
            envelope.id,
            envelope.zipFileName,
            envelope.jurisdiction,
            envelope.formType == null ? "" : envelope.formType,
            envelope.classification,
            envelope.caseRef == null ? "Legacy Case" : "Case",
            envelope.caseRef == null ? envelope.legacyCaseRef : envelope.caseRef
        );
    }

    private void logMessageProcessingError(IMessage message, Envelope envelope, Exception exception) {
        String baseMessage = String.format("Failed to process message with ID %s.", message.getMessageId());

        String fullMessage = envelope != null
            ? baseMessage + String.format(" Envelope ID: %s, File name: %s", envelope.id, envelope.zipFileName)
            : baseMessage;

        log.error(fullMessage, exception);
    }
}
