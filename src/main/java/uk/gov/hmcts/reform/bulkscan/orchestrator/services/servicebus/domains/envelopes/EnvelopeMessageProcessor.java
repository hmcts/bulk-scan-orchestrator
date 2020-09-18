package uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes;

import com.microsoft.azure.servicebus.primitives.ServiceBusException;
import org.apache.qpid.jms.JmsMessageConsumer;
import org.apache.qpid.jms.message.JmsMessage;
import org.apache.qpid.jms.message.JmsMessageSupport;
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

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.TextMessage;

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
    private final MessageConsumer messageReceiver;
    private final int maxDeliveryCount;
    private final AppInsights appInsights;

    public EnvelopeMessageProcessor(
        EnvelopeHandler envelopeHandler,
        IProcessedEnvelopeNotifier processedEnvelopeNotifier,
        MessageConsumer messageReceiver,
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
    public boolean processNextMessage() throws JMSException {
        Message message = messageReceiver.receiveNoWait();


        if (message != null) {
//            log.info("Started processing message with ID {}", message.getMessageId());
            log.info("Started processing message with ID {}", message.getJMSMessageID());
            MessageProcessingResult result = process(message);
            tryFinaliseProcessedMessage(message, result);
        } else {
            log.info("No envelope messages left to process");
        }

        return message != null;
    }

    private MessageProcessingResult process(Message message) throws JMSException {
        // XXX label would need to be send as property (or would need to check how that hearbeat is produced)
        if (Objects.equals(message.getStringProperty("LABEL"), HEARTBEAT_LABEL)) {
            log.info("Heartbeat message received");
            return new MessageProcessingResult(SUCCESS);
        } else {
            Envelope envelope = null;

            try {
                TextMessage txtMessage = (TextMessage) message;
                envelope = parse(txtMessage.getText());
                logMessageParsed(message, envelope);
                // WOULD need to deal with the delivery count somehow
                EnvelopeProcessingResult envelopeProcessingResult =
                    envelopeHandler.handleEnvelope(envelope, ((JmsMessage) message).getFacade().getDeliveryCount());
                processedEnvelopeNotifier.notify(
                    envelope.id,
                    envelopeProcessingResult.ccdId,
                    envelopeProcessingResult.envelopeCcdAction
                );
                log.info(
                    "Processed message with ID {}. File name: {}",
                    message.getJMSMessageID(),
                    envelope.zipFileName
                );
                return new MessageProcessingResult(SUCCESS);
            } catch (InvalidMessageException ex) {
                log.error("Rejected message with ID {}, because it's invalid", message.getJMSMessageID(), ex);
                return new MessageProcessingResult(UNRECOVERABLE_FAILURE, ex);
            } catch (Exception ex) {
                logMessageProcessingError(message, envelope, ex);
                return new MessageProcessingResult(POTENTIALLY_RECOVERABLE_FAILURE);
            }
        }
    }

    private void tryFinaliseProcessedMessage(Message message, MessageProcessingResult processingResult) throws JMSException {
        try {
            finaliseProcessedMessage((JmsMessage) message, processingResult);
        } catch (Exception ex) {
            logMessageFinaliseError(message, processingResult.resultType, ex);
        }
    }

    private void finaliseProcessedMessage(
        JmsMessage message,
        MessageProcessingResult processingResult
    ) throws  JMSException {

        switch (processingResult.resultType) {
            case SUCCESS:
//                messageReceiver.complete(message.getLockToken());
                message.acknowledge();
                log.info("Message with ID {} has been completed", message.getJMSMessageID());
                break;
            case UNRECOVERABLE_FAILURE:
                deadLetterTheMessage(
                    message,
                    "Message processing error",
                    processingResult.exception.getMessage()
                );

                break;
            case POTENTIALLY_RECOVERABLE_FAILURE:
                // delivery count is actual deliver count - starts from 1
                int deliveryCount = message.getFacade().getDeliveryCount();

                if (deliveryCount < maxDeliveryCount) {
                    // do nothing - let the message lock expire
                    log.info(
                        "Allowing message with ID {} to return to queue (delivery attempt {})",
                        message.getJMSMessageID(),
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
        Message message,
        String reason,
        String description
    ) throws JMSException {
        JmsMessage jmsMessage = (JmsMessage) message;
        jmsMessage.getAcknowledgeCallback().setAckType(JmsMessageSupport.REJECTED);
        //XXX unfortunately that doesn't work, the reason/description not added
        jmsMessage.setReadOnly(false);
        jmsMessage.setReadOnlyProperties(false);
        // can't add extra description
        jmsMessage.setStringProperty("description", description);
        jmsMessage.setStringProperty("reason", reason);
        jmsMessage.setReadOnly(true);
        jmsMessage.setReadOnlyProperties(true);
        //XXX annoying API, no easier way of rejecting message found only via callback
        message.acknowledge();
//        messageProducer.send(message);
//        messageReceiver.acknowledge(ProviderConstants.ACK_TYPE.REJECTED);
//        messageReceiver.deadLetter(
//            message.getLockToken(),
//            reason,
//            description,
//            ImmutableMap.of("deadLetteredAt", Instant.now().toString())
//        );
        log.info("Message with ID {} has been dead-lettered", message.getJMSMessageID());
        // track used for alert
        appInsights.trackDeadLetteredMessage(message, "envelopes", reason, description);
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

    private void logMessageParsed(Message message, Envelope envelope) throws JMSException {
        log.info(
            "Parsed message. ID: {}, Envelope ID: {}, File name: {}, Jurisdiction: {}, Form type: {}, "
                + "Classification: {}, {}: {}",
            message.getJMSMessageID(),
            envelope.id,
            envelope.zipFileName,
            envelope.jurisdiction,
            envelope.formType == null ? "" : envelope.formType,
            envelope.classification,
            envelope.caseRef == null ? "Legacy Case" : "Case",
            envelope.caseRef == null ? envelope.legacyCaseRef : envelope.caseRef
        );
    }

    private void logMessageProcessingError(Message message, Envelope envelope, Exception exception) throws JMSException {
        String baseMessage = String.format("Failed to process message with ID %s.", message.getJMSMessageID());

        String fullMessage = envelope != null
            ? baseMessage + String.format(" Envelope ID: %s, File name: %s", envelope.id, envelope.zipFileName)
            : baseMessage;

        log.error(fullMessage, exception);
    }
}
