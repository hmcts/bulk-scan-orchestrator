package uk.gov.hmcts.reform.bulkscan.orchestrator.tasks.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
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
    private final int retryCount;

    public PaymentProcessingTask(PaymentService paymentService, PaymentApiClient paymentApiClient,
                                 @Value("${scheduling.task.post-payments.retry-count}") int retryCount) {
        this.paymentService = paymentService;
        this.paymentApiClient = paymentApiClient;
        this.retryCount = retryCount;
    }

    @Scheduled(fixedDelayString = "${scheduling.task.post-payments.interval}")
    public void processPayments() {

        try {
            List<Payment> payments = paymentService.getPaymentsByStatus(Status.AWAITING.toString());

            if (!payments.isEmpty()) {

                payments.forEach(payment -> {

                    log.info("Posting payment to payment api client for envelope. {}", payment.getEnvelopeId());

                    ResponseEntity<String> responseEntity = postPaymentsToPaymentApi(payment, retryCount);

                    if (responseEntity.getStatusCode().is2xxSuccessful()) {

                        log.info("Posting payment was successful for envelope. {}", payment.getEnvelopeId());
                        paymentService.updateStatusByEnvelopeId(Status.SUCCESS.toString(), payment.getEnvelopeId());
                        log.info("Updated payment status to success for envelope. {}", payment.getEnvelopeId());
                    } else {
                        log.info("Posting payment was unsuccessful for envelope. {}", payment.getEnvelopeId());
                        paymentService.updateStatusByEnvelopeId(Status.ERROR.toString(), payment.getEnvelopeId());
                        log.info("Updated payment status to error for envelope. {}", payment.getEnvelopeId());
                    }
                });
            }
        } catch (Exception e) {
            log.info("Error posting payments to payment api client. {}", e.getMessage());
        }
    }

    private ResponseEntity<String> postPaymentsToPaymentApi(Payment payment, int retryCount) {

        if (retryCount > 0) {

            ResponseEntity<String> responseEntity = paymentApiClient.postPayment(payment);

            if (responseEntity.getStatusCode().is2xxSuccessful()) {

                return responseEntity;
            } else {
                postPaymentsToPaymentApi(payment, --retryCount);
            }
        }
        return new ResponseEntity<>("Attempted 3 times", HttpStatus.REQUEST_TIMEOUT);
    }
}
