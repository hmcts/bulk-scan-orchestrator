package uk.gov.hmcts.reform.bulkscan.orchestrator.services;

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
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.IProcessedEnvelopeNotifier;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.NotificationSendingException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.exceptions.InvalidMessageException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.exceptions.MessageProcessingException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.handler.MessageProcessingResult;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.handler.MessageProcessingResultType;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;

import java.time.Instant;

import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.EnvelopeParser.parse;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.handler.MessageProcessingResultType.POTENTIALLY_RECOVERABLE_FAILURE;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.handler.MessageProcessingResultType.SUCCESS;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.handler.MessageProcessingResultType.UNRECOVERABLE_FAILURE;

@Service
// TODO: change name to EnvelopeMessageProcessor
public class EnvelopeEventProcessor {

    private static final Logger log = LoggerFactory.getLogger(EnvelopeEventProcessor.class);

    private final EnvelopeHandler envelopeHandler;
    private final IProcessedEnvelopeNotifier processedEnvelopeNotifier;
    private final IMessageReceiver messageReceiver;
    private final int maxDeliveryCount;
    private final AppInsights appInsights;

    public EnvelopeEventProcessor(
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
            MessageProcessingResult result = process(message);
            tryFinaliseProcessedMessage(message, result);
        }

        return message != null;
    }

    @SuppressWarnings("squid:CallToDeprecatedMethod") // for sonarqube complaining about deprecated things being used
    private MessageProcessingResult process(IMessage message) {
        log.info("Started processing message with ID {}", message.getMessageId());

        Envelope envelope = null;

        try {
            envelope = parse(message.getBody());
            logMessageParsed(message, envelope);
            envelopeHandler.handleEnvelope(envelope);
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
}
