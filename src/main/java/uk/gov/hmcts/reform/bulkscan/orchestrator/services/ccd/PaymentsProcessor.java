package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.payment.Payment;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.payment.PaymentData;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.callback.PaymentsHelper;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.payment.PaymentService;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Envelope;

import java.util.Optional;

import static java.util.stream.Collectors.toList;

@Service
public class PaymentsProcessor {

    private static final Logger log = LoggerFactory.getLogger(PaymentsProcessor.class);

    private static final String Status  = "awaiting";
    private final PaymentService paymentService;

    public PaymentsProcessor(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    // adds the payment record to the payment table if a payment exists.
    public void createPayments(Envelope envelope, Long caseId, boolean isExceptionRecord) {
        if (envelope.payments != null && !envelope.payments.isEmpty()) {
            Payment payment = new Payment(
                envelope.id,
                Long.toString(caseId),
                envelope.jurisdiction,
                envelope.container,
                envelope.poBox,
                isExceptionRecord,
                Status,
                envelope.payments.stream()
                    .map(pay -> new PaymentData(pay.documentControlNumber))
                    .collect(toList())
            );

            paymentService.savePayment(payment);
            log.info("Started processing payments for case with CCD reference {}", payment.ccdReference);
            log.info("Finished processing payments for case with CCD reference {}", payment.ccdReference);
        } else {
            log.info(
                "Envelope has no payments, not sending create command. Envelope id: {}. Case reference {}",
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

            log.info("Contains Payments, sending payment update message. ER id: {}", exceptionRecordId);

            //            paymentsPublisher.send(
            //                new UpdatePaymentsCommand(
            //                    exceptionRecordId,
            //                    newCaseId,
            //                    paymentsHelper.envelopeId,
            //                    jurisdiction
            //                )
            //            );
            log.info("Finished sending payment update message. ER id: {}", exceptionRecordId);

        } else {
            log.info(
                "Exception record has no payments, not sending update command. ER id: {}",
                exceptionRecordId
            );
        }
    }
}
