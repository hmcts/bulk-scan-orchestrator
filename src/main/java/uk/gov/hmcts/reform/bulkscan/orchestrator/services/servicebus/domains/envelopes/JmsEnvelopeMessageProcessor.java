package uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.envelopehandlers.EnvelopeHandler;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.handler.MessageProcessingResult;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.handler.MessageProcessingResultType;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Envelope;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.processedenvelopes.EnvelopeProcessingResult;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.processedenvelopes.IProcessedEnvelopeNotifier;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.exceptions.InvalidMessageException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.exceptions.MessageProcessingException;

import java.nio.charset.StandardCharsets;
import javax.jms.JMSException;
import javax.jms.Message;

import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.EnvelopeParser.parse;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.handler.MessageProcessingResultType.POTENTIALLY_RECOVERABLE_FAILURE;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.handler.MessageProcessingResultType.SUCCESS;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.handler.MessageProcessingResultType.UNRECOVERABLE_FAILURE;

@Service
@ConditionalOnExpression("${jms.enabled}")
public class JmsEnvelopeMessageProcessor {

    private static final Logger log = LoggerFactory.getLogger(JmsEnvelopeMessageProcessor.class);

    private final EnvelopeHandler envelopeHandler;
    private final IProcessedEnvelopeNotifier processedEnvelopeNotifier;
    private final int maxDeliveryCount;

    public JmsEnvelopeMessageProcessor(
        EnvelopeHandler envelopeHandler,
        IProcessedEnvelopeNotifier processedEnvelopeNotifier,
        @Value("${azure.servicebus.envelopes.max-delivery-count}") int maxDeliveryCount
    ) {
        this.envelopeHandler = envelopeHandler;
        this.processedEnvelopeNotifier = processedEnvelopeNotifier;
        this.maxDeliveryCount = maxDeliveryCount;
    }

    /**
     * Reads and processes next message from the queue.
     * return false if there was no message to process. Otherwise, true.
     */
    public void processMessage(Message context, String messageBody) throws JMSException {
        if (context != null && !messageBody.isEmpty()) {
            log.info("Started processing message with ID {}", context.getJMSMessageID());
            MessageProcessingResult result = process(context, messageBody,
                Long.parseLong(context.getStringProperty("JMSXDeliveryCount")));
            tryFinaliseProcessedMessage(context, result);
        } else {
            log.info("No envelope messages left to process");
        }
    }

    private MessageProcessingResult process(Message message, String messageBody, long deliveryCount)
        throws JMSException {

        Envelope envelope = null;
        try {
            envelope = parse(messageBody.getBytes(StandardCharsets.UTF_8));
            logMessageParsed(messageBody, envelope);
            EnvelopeProcessingResult envelopeProcessingResult =
                envelopeHandler.handleEnvelope(envelope, deliveryCount);
            processedEnvelopeNotifier.notify(
                envelope.id,
                envelopeProcessingResult.ccdId,
                envelopeProcessingResult.envelopeCcdAction
            );
            log.info("Processed message with ID {}. File name: {}", message.getJMSMessageID(), envelope.zipFileName);
            return new MessageProcessingResult(SUCCESS);
        } catch (InvalidMessageException ex) {
            log.error("Rejected message with ID {}, because it's invalid", message.getJMSMessageID(), ex);
            return new MessageProcessingResult(UNRECOVERABLE_FAILURE, ex);
        } catch (Exception ex) {
            logMessageProcessingError(messageBody, envelope, ex);
            return new MessageProcessingResult(POTENTIALLY_RECOVERABLE_FAILURE);
        }
    }

    private void tryFinaliseProcessedMessage(
        Message context,
        MessageProcessingResult processingResult
    ) throws JMSException {
        try {
            finaliseProcessedMessage(context, processingResult);
        } catch (InterruptedException ex) {
            logMessageFinaliseError(context, processingResult.resultType, ex);
            Thread.currentThread().interrupt();
        } catch (Exception ex) {
            logMessageFinaliseError(context, processingResult.resultType, ex);
        }
    }

    private void finaliseProcessedMessage(
        Message context,
        MessageProcessingResult processingResult
    ) throws InterruptedException, JMSException {
        // starts from 0
        switch (processingResult.resultType) {
            case SUCCESS -> {
                context.acknowledge();
                log.info("Message with ID {} has been completed", context.getJMSMessageID());
            }
            case UNRECOVERABLE_FAILURE -> deadLetterTheMessage(
                context,
                "Message processing error",
                processingResult.exception.getMessage()
            );
            case POTENTIALLY_RECOVERABLE_FAILURE -> {
                int deliveryCount = Integer.parseInt(context.getStringProperty("JMSXDeliveryCount")) + 1;
                if (deliveryCount < maxDeliveryCount) {
                    // do nothing - let the message lock expire
                    log.info(
                        "Allowing message with ID {} to return to queue (delivery attempt {})",
                        context.getJMSMessageID(),
                        deliveryCount
                    );
                } else {
                    deadLetterTheMessage(
                        context,
                        "Too many deliveries",
                        "Reached limit of message delivery count of " + deliveryCount
                    );
                }
            }
            default -> throw new MessageProcessingException(
                "Unknown message processing result type: " + processingResult.resultType
            );
        }
    }

    private void deadLetterTheMessage(
        Message context,
        String reason,
        String description
    ) throws JMSException {
        log.info("Message with ID {} has been dead-lettered...if this was using ASB, reason was {}: "
                + "description was: {}",
            context.getJMSMessageID(), reason, description);
    }

    private void logMessageFinaliseError(
        Message message,
        MessageProcessingResultType processingResultType,
        Exception ex
    ) throws JMSException {
        log.error(
            "Failed to manage processed message with ID {}. Processing result: {}",
            message.getJMSMessageID(),
            processingResultType,
            ex
        );
    }

    private void logMessageParsed(String message, Envelope envelope) {
        log.info(
            "Parsed message: {}, Envelope ID: {}, File name: {}, Container: {}, Jurisdiction: {}, Form type: {}, "
                + "Classification: {}, {}: {}",
            message,
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

    private void logMessageProcessingError(String messageBody, Envelope envelope, Exception exception) {
        String baseMessage = String.format("Failed to process message: %s.", messageBody);

        String fullMessage = envelope != null
            ? baseMessage + String.format(" Envelope ID: %s, File name: %s", envelope.id, envelope.zipFileName)
            : baseMessage;

        log.error(fullMessage, exception);
    }
}
