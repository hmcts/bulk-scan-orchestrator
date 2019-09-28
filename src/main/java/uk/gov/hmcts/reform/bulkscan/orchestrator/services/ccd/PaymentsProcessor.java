package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.IPaymentsPublisher;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.PaymentData;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.PaymentsData;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import static java.util.stream.Collectors.toList;

@Service
public class PaymentsProcessor {
    private static final Logger log = LoggerFactory.getLogger(PaymentsProcessor.class);

    private final IPaymentsPublisher paymentsPublisher;

    public PaymentsProcessor(
        IPaymentsPublisher paymentsPublisher
    ) {
        this.paymentsPublisher = paymentsPublisher;
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

            log.info("Started processing payments for case with CCD reference {}", paymentsData.ccdReference);
            paymentsPublisher.publishPayments(paymentsData);
        }
    }
}
