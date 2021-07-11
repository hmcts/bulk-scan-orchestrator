package uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes;

import com.azure.messaging.servicebus.ServiceBusErrorContext;
import com.azure.messaging.servicebus.ServiceBusReceivedMessage;
import com.azure.messaging.servicebus.ServiceBusReceivedMessageContext;
import com.azure.messaging.servicebus.models.DeadLetterOptions;
import com.google.common.collect.ImmutableMap;
import com.microsoft.azure.servicebus.primitives.ServiceBusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.bulkscan.orchestrator.logging.AppInsights;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.envelopehandlers.EnvelopeHandler;
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
    private final int maxDeliveryCount;
    private final AppInsights appInsights;

    public EnvelopeMessageProcessor(
        EnvelopeHandler envelopeHandler,
        IProcessedEnvelopeNotifier processedEnvelopeNotifier,
        @Value("${azure.servicebus.envelopes.max-delivery-count}") int maxDeliveryCount,
        AppInsights appInsights
    ) {
        this.envelopeHandler = envelopeHandler;
        this.processedEnvelopeNotifier = processedEnvelopeNotifier;
        this.maxDeliveryCount = maxDeliveryCount;
        this.appInsights = appInsights;
    }

    /**
     * Reads and processes next message from the queue.
     *
     * @return false if there was no message to process. Otherwise true.
     */
    public void processMessage(ServiceBusReceivedMessageContext context) {
        ServiceBusReceivedMessage message = context.getMessage();

        if (message != null) {
            log.info("Started processing message with ID {}", message.getMessageId());
            MessageProcessingResult result = process(message);
            tryFinaliseProcessedMessage(context, result);
        } else {
            log.info("No envelope messages left to process");
        }

    }

    private MessageProcessingResult process(ServiceBusReceivedMessage message) {
        if (Objects.equals(message.getSubject(), HEARTBEAT_LABEL)) {
            log.info("Heartbeat message received");
            return new MessageProcessingResult(SUCCESS);
        } else {
            Envelope envelope = null;

            try {
                envelope = parse(message.getBody().toBytes());
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

    private void tryFinaliseProcessedMessage(
        ServiceBusReceivedMessageContext context,
        MessageProcessingResult processingResult
    ) {
        var message = context.getMessage();
        try {
            finaliseProcessedMessage(context, processingResult);
        } catch (InterruptedException ex) {
            logMessageFinaliseError(message, processingResult.resultType, ex);
            Thread.currentThread().interrupt();
        } catch (Exception ex) {
            logMessageFinaliseError(message, processingResult.resultType, ex);
        }
    }

    private void finaliseProcessedMessage(
        ServiceBusReceivedMessageContext context,
        MessageProcessingResult processingResult
    ) throws InterruptedException, ServiceBusException {
        var message = context.getMessage();
        switch (processingResult.resultType) {
            case SUCCESS:
                context.complete();;
                log.info("Message with ID {} has been completed", message.getMessageId());
                break;
            case UNRECOVERABLE_FAILURE:
                deadLetterTheMessage(
                    context,
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
                        context,
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
        ServiceBusReceivedMessageContext context,
        String reason,
        String description
    ) {
        context.deadLetter(
            new DeadLetterOptions()
                .setDeadLetterReason(reason)
                .setDeadLetterErrorDescription(description)
                .setPropertiesToModify(ImmutableMap.of("deadLetteredAt", Instant.now().toString()))
        );
        var message = context.getMessage();
        log.info("Message with ID {} has been dead-lettered", message.getMessageId());
        // track used for alert
        appInsights.trackDeadLetteredMessage(message, "envelopes", reason, description);
    }

    private void logMessageFinaliseError(
        ServiceBusReceivedMessage message,
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

    private void logMessageParsed(ServiceBusReceivedMessage message, Envelope envelope) {
        log.info(
            "Parsed message. ID: {}, Envelope ID: {}, File name: {}, Container: {}, Jurisdiction: {}, Form type: {}, "
                + "Classification: {}, {}: {}",
            message.getMessageId(),
            envelope.id,
            envelope.zipFileName,
            envelope.container,
            envelope.jurisdiction,
            envelope.formType == null ? "" : envelope.formType,
            envelope.classification,
            envelope.caseRef == null ? "Legacy Case" : "Case",
            envelope.caseRef == null ? envelope.legacyCaseRef : envelope.caseRef
        );
    }

    private void logMessageProcessingError(ServiceBusReceivedMessage message, Envelope envelope, Exception exception) {
        String baseMessage = String.format("Failed to process message with ID %s.", message.getMessageId());

        String fullMessage = envelope != null
            ? baseMessage + String.format(" Envelope ID: %s, File name: %s", envelope.id, envelope.zipFileName)
            : baseMessage;

        log.error(fullMessage, exception);
    }

    public void processException(ServiceBusErrorContext context) {
        log.error("Processed envelope queue handle error {}", context.getErrorSource(), context.getException());
    }
}
