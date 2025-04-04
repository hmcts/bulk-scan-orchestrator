package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.payment.Payment;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.payment.PaymentData;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.payment.Status;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.payment.UpdatePayment;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.callback.PaymentsHelper;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.payment.PaymentService;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.payment.UpdatePaymentService;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Envelope;

import java.time.Instant;
import java.util.Optional;

@Service
public class PaymentsProcessor {

    private static final Logger log = LoggerFactory.getLogger(PaymentsProcessor.class);

    private final PaymentService paymentService;
    private final UpdatePaymentService updatePaymentService;

    public PaymentsProcessor(PaymentService paymentService, UpdatePaymentService updatePaymentService) {
        this.paymentService = paymentService;
        this.updatePaymentService = updatePaymentService;
    }

    public void createPayments(Envelope envelope, Long caseId, boolean isExceptionRecord) {
        if (envelope.payments != null && !envelope.payments.isEmpty()) {

            log.info("Started saving payments for case with CCD reference {}", caseId);
            Payment payment = new Payment(
                envelope.id,
                Instant.now(),
                Long.toString(caseId),
                envelope.jurisdiction,
                envelope.container,
                envelope.poBox,
                isExceptionRecord,
                Status.AWAITING.toString(),
                envelope.payments.stream().map(pay -> new PaymentData(pay.documentControlNumber)).toList());

            paymentService.savePayment(payment);
            log.info("Finished saving payments for case with CCD reference {}", caseId);
        } else {
            log.info(
                "Envelope has no payments, not saving payment. Envelope id: {}. Case reference {}",
                envelope.id,
                Optional.ofNullable(envelope.caseRef).orElse("(NOT PRESENT)")
            );
        }
    }

    public void updatePayments(
        PaymentsHelper paymentsHelper,
        String exceptionRecordId,
        String jurisdiction,
        String newCaseId
    ) {
        if (paymentsHelper.containsPayments) {

            log.info("Contains Payments, saving payment update message. ER id: {}", exceptionRecordId);

            UpdatePayment updatePayment = new UpdatePayment(
                Instant.now(),
                exceptionRecordId,
                newCaseId,
                paymentsHelper.envelopeId,
                jurisdiction,
                Status.AWAITING.toString()
            );
            updatePaymentService.savePayment(updatePayment);

            log.info("Finished saving payment update message. ER id: {}", exceptionRecordId);

        } else {
            log.info(
                "Exception record has no payments, not saving update command. ER id: {}",
                exceptionRecordId
            );
        }
    }
}
