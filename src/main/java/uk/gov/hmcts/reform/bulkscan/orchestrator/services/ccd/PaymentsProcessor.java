package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import com.google.common.collect.ImmutableMap;
import com.microsoft.azure.servicebus.IMessage;
import com.microsoft.azure.servicebus.primitives.ServiceBusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.EnvelopeEventProcessor;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.IPaymentsPublisher;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.MessageBodyRetriever;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.NotificationSendingException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.exceptions.InvalidMessageException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.exceptions.MessageProcessingException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.handler.MessageProcessingResult;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.handler.MessageProcessingResultType;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.PaymentData;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.PaymentsData;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.time.Instant;
import java.util.Objects;

import static java.util.stream.Collectors.toList;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.handler.MessageProcessingResultType.POTENTIALLY_RECOVERABLE_FAILURE;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.handler.MessageProcessingResultType.SUCCESS;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.handler.MessageProcessingResultType.UNRECOVERABLE_FAILURE;

@Component
public class PaymentsProcessor {
    private static final Logger log = LoggerFactory.getLogger(PaymentsProcessor.class);

    private final IPaymentsPublisher paymentsPublisher;
    private final int maxDeliveryCount;

    public PaymentsProcessor(
        IPaymentsPublisher paymentsPublisher,
        @Value("${azure.servicebus.payments.max-delivery-count}") int maxDeliveryCount
    ) {
        this.paymentsPublisher = paymentsPublisher;
        this.maxDeliveryCount = maxDeliveryCount;
    }

    public void processPayments(Envelope envelope, CaseDetails caseDetails, boolean isExceptionRecord) {
        if (envelope.payments != null && !envelope.payments.isEmpty()) {
            PaymentsData paymentsData = new PaymentsData(
                Long.toString(caseDetails.getId()),
                envelope.jurisdiction,
                envelope.poBox,
                isExceptionRecord,
                envelope.payments.stream()
                    .map(payment -> new PaymentData(payment.documentControlNumber))
                    .collect(toList())
            );
            log.info("Started processing message with ID {}", message.getMessageId());
            MessageProcessingResult result = process(message);
            tryFinaliseProcessedMessage(message, result);
        }
    }

    private MessageProcessingResult process(PaymentsData paymentsData) {
            Envelope envelope = null;

            try {
                paymentsPublisher.publishPayments(paymentsData);
                log.info("Processed payments for case with CCD reference {}", paymentsData.ccdReference);
                return new MessageProcessingResult(SUCCESS);
            } catch (NotificationSendingException ex) {
                logPaymentsProcessingError(paymentsData, ex);
                return new MessageProcessingResult(UNRECOVERABLE_FAILURE, ex);
            } catch (Exception ex) {
                logPaymentsProcessingError(paymentsData, ex);
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

    private void logPaymentsProcessingError(PaymentsData paymentsData, Exception exception) {
        String msg = String.format("Failed to process payments for case with CCD reference %s.", paymentsData.ccdReference);

        log.error(msg, exception);
    }
}
