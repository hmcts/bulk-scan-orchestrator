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
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.payment.Status;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.payment.UpdatePayment;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.payment.UpdatePaymentService;

import java.util.List;

@Component
@ConditionalOnProperty(value = "scheduling.task.enabled", matchIfMissing = true)
public class UpdatePaymentProcessingTask {

    private static final Logger log = LoggerFactory.getLogger(UpdatePaymentProcessingTask.class);
    private final UpdatePaymentService updatePaymentService;
    private final PaymentApiClient paymentApiClient;
    private final int maxRetry;

    public UpdatePaymentProcessingTask(UpdatePaymentService updatePaymentService,
                                       PaymentApiClient paymentApiClient,
                                       @Value("${scheduling.task.post-payments.max-retry}") int maxRetry) {
        this.updatePaymentService = updatePaymentService;
        this.paymentApiClient = paymentApiClient;
        this.maxRetry = maxRetry;
    }

    /**
     * Responsible for getting the payment updates that are awaiting being processed.
     * The task occurs according to a certain interval set by the PAYMENT_PROCESSING_INTERVAL
     * environment variable. The task gathers all the payment update items from the database that
     * are awaiting processing, and sends them to Bulk Scan Payment Processor.
     * If there is a failure processing a payment, then it will be retried according to the
     * retry amount set in the application config.
     */
    @Scheduled(fixedDelayString = "${scheduling.task.post-payments.interval}")
    public void processUpdatePayments() {

        try {
            List<UpdatePayment> updatePayments =
                updatePaymentService.getUpdatePaymentByStatus(Status.AWAITING.toString());

            if (!updatePayments.isEmpty()) {

                updatePayments.forEach(payment -> {

                    log.info("Posting update payment to payment api client for envelope. {}", payment.getEnvelopeId());

                    ResponseEntity<String> responseEntity = postPaymentsToPaymentApi(payment, maxRetry);

                    if (responseEntity.getStatusCode().is2xxSuccessful()) {
                        updatePaymentService.updateStatusByEnvelopeId(Status.SUCCESS.toString(),
                            payment.getEnvelopeId());
                        log.info("Updated payment status to success for envelope. {}", payment.getEnvelopeId());
                    } else {
                        updatePaymentService.updateStatusByEnvelopeId(Status.ERROR.toString(), payment.getEnvelopeId());
                        log.info("Updated update payment status to error for envelope. {}", payment.getEnvelopeId());
                    }
                });
            }
        } catch (Exception e) {
            log.info("Error posting update payments to payment api client. {}", e.getMessage());
        }
    }

    /**
     * Sends a payment update to Bulk Scan Payment Processor (BSPP) API endpoint.
     * If BSPP returns a failure status code (4xx, 5xx), a child of the HttpStatusCodeException will be thrown. This
     * exception is caught the method calls itself again (recursive) with a reduction in the amount of times it
     * should retry. If the amount of retries reached 0, the method will return 422.
     * @param updatePayment - info on how payment should be updated
     * @param maxRetry - the maximum of times the method should try to send the payment to BSPP
     * @return ResponseEntity - 200 (OK) if successfully calls BSPP, 422 (FAILED DEPENDENCY) if not
     */
    private ResponseEntity<String> postPaymentsToPaymentApi(UpdatePayment updatePayment, int maxRetry) {
        if (maxRetry > 0) {
            try {
                return paymentApiClient.postUpdatePayment(updatePayment);
            }
            catch (HttpStatusCodeException e) {
                log.error("Failed send payment to payment API. Status code {}, with body {},  Envelope ID {}",
                    e.getStatusCode(), e.getResponseBodyAsString(), updatePayment.getEnvelopeId());
                return postPaymentsToPaymentApi(updatePayment, --maxRetry);
            }
        }
        return new ResponseEntity<>("All attempts to post payment update to payment API have failed. Envelope ID: "
            + updatePayment.getEnvelopeId()
            , HttpStatus.FAILED_DEPENDENCY);
    }

}
