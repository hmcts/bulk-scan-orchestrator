package uk.gov.hmcts.reform.bulkscan.orchestrator.tasks.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.payment.PaymentApiClient;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.payment.Payment;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.payment.Status;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.payment.PaymentService;

import java.util.List;

@Component
@ConditionalOnProperty(value = "scheduling.task.enabled", matchIfMissing = true)
public class PaymentProcessingTask {

    private static final Logger log = LoggerFactory.getLogger(PaymentProcessingTask.class);
    private final PaymentService paymentService;
    private final PaymentApiClient paymentApiClient;
    private final int maxRetry;

    public PaymentProcessingTask(PaymentService paymentService, PaymentApiClient paymentApiClient,
                                 @Value("${scheduling.task.post-payments.max-retry}") int maxRetry) {
        this.paymentService = paymentService;
        this.paymentApiClient = paymentApiClient;
        this.maxRetry = maxRetry;
    }

    /**
     * Responsible for getting the payments that are awaiting being processed.
     * The task occurs according to a certain interval set by the PAYMENT_PROCESSING_INTERVAL
     * environment variable. The task gathers all the payment items from the database that
     * are awaiting processing, and sends them to Bulk Scan Payment Processor.
     * If there is a failure processing a payment, then it will be retried according to the
     * retry amount set in the application config.
     */
    @Scheduled(fixedDelayString = "${scheduling.task.post-payments.interval}")
    public void processPayments() {

        try {
            List<Payment> payments = paymentService.getPaymentsByStatus(Status.AWAITING.toString());

            if (!payments.isEmpty()) {

                payments.forEach(payment -> {

                    log.info("Posting payment to payment api client for envelope. {}", payment.getEnvelopeId());

                    ResponseEntity<String> responseEntity = postPaymentsToPaymentApi(payment, maxRetry);

                    if (responseEntity.getStatusCode().is2xxSuccessful()) {

                        paymentService.updateStatusByEnvelopeId(Status.SUCCESS.toString(), payment.getEnvelopeId());
                        log.info("Updated payment status to success for envelope. {}", payment.getEnvelopeId());
                    } else {
                        paymentService.updateStatusByEnvelopeId(Status.ERROR.toString(), payment.getEnvelopeId());
                        log.error("Updated payment status to error for envelope. {}", payment.getEnvelopeId());
                    }
                });
            }
        } catch (Exception e) {
            log.error("Error posting payments to payment API client", e);
            throw new PaymentProcessingException("Failed to post payments to payment API client", e);
        }
    }

    /**
     * Sends payments to Bulk Scan Payment Processor (BSPP) API endpoint.
     * If sending the payment to BSPP fails, the method will call itself again (recursive) and reduce
     * the maxRetry parameter by 1. If the maxRetry reaches 0, the method will return 422 failed dependency to
     * represent the fact that all attempts to send payment to BSPP has failed.
     * @param payment - the payment to be sent to BSPP
     * @param maxRetry - the amount of times the method should try and send to BSPP
     * @return ResponseEntity - 200 if call to BSPP is successfully, 422 if not
     */
    private ResponseEntity<String> postPaymentsToPaymentApi(Payment payment, int maxRetry) {

        if (maxRetry > 0) {
            try {
                return paymentApiClient.postPayment(payment);
            } catch (HttpStatusCodeException e) {
                log.error("Failed send payment to payment API. Status code {}, with body {},  Envelope ID {}. "
                        + "Attempts remaining {}",
                    e.getStatusCode(), e.getResponseBodyAsString(), payment.getEnvelopeId(), maxRetry);
                return postPaymentsToPaymentApi(payment, --maxRetry);
            }
        }
        return new ResponseEntity<>("All attempts to post payment to payment API have failed. Envelope ID: "
            + payment.getEnvelopeId(), HttpStatus.FAILED_DEPENDENCY);
    }
}
