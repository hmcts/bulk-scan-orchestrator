package uk.gov.hmcts.reform.bulkscan.orchestrator.tasks.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.payment.PaymentApiClient;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.payment.Status;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.payment.UpdatePayment;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.payment.UpdatePaymentService;

import java.util.List;

@Component
public class UpdatePaymentProcessingTask {

    private static final Logger log = LoggerFactory.getLogger(UpdatePaymentProcessingTask.class);
    private final UpdatePaymentService updatePaymentService;
    private final PaymentApiClient paymentApiClient;
    private final int retryCount;

    public UpdatePaymentProcessingTask(UpdatePaymentService updatePaymentService,
                                       PaymentApiClient paymentApiClient,
                                       @Value("${scheduling.task.post-payments.retry-count}") int retryCount) {
        this.updatePaymentService = updatePaymentService;
        this.paymentApiClient = paymentApiClient;
        this.retryCount = retryCount;
    }

    @Scheduled(fixedDelayString = "${scheduling.task.post-payments.interval}")
    public void processUpdatePayments() {

        try {
            List<UpdatePayment> updatePayments =
                updatePaymentService.getUpdatePaymentByStatus(Status.AWAITING.toString());

            if (!updatePayments.isEmpty()) {

                updatePayments.forEach(payment -> {

                    log.info("Posting update payment to payment api client for envelope. {}", payment.getEnvelopeId());

                    ResponseEntity<String> responseEntity = postPaymentsToPaymentApi(payment, retryCount);

                    if (responseEntity.getStatusCode().is2xxSuccessful()) {

                        log.info("Posting update payment was successful for envelope. {}",
                            payment.getEnvelopeId());
                        updatePaymentService.updateStatusByEnvelopeId(Status.SUCCESS.toString(),
                            payment.getEnvelopeId());
                        log.info("Updated payment status to success for envelope. {}", payment.getEnvelopeId());
                    } else {
                        log.info("Posting update payment was unsuccessful for envelope. {}",
                            payment.getEnvelopeId());
                        updatePaymentService.updateStatusByEnvelopeId(Status.ERROR.toString(), payment.getEnvelopeId());
                        log.info("Updated update payment status to error for envelope. {}", payment.getEnvelopeId());
                    }
                });
            }
        } catch (Exception e) {
            log.info("Error posting update payments to payment api client. {}", e.getMessage());
        }
    }

    private ResponseEntity<String> postPaymentsToPaymentApi(UpdatePayment updatePayment, int retryCount) {
        if (retryCount > 0) {

            ResponseEntity<String> responseEntity = paymentApiClient.postUpdatePayment(updatePayment);

            if (responseEntity.getStatusCode().is2xxSuccessful()) {

                return responseEntity;
            } else {
                postPaymentsToPaymentApi(updatePayment, --retryCount);
            }

        }
        return new ResponseEntity<>("Attempted 3 times", HttpStatus.REQUEST_TIMEOUT);

    }

}
