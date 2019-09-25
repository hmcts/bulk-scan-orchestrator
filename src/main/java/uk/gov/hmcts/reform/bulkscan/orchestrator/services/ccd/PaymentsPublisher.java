package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.PaymentsData;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import static java.util.stream.Collectors.toList;

@Component
public class PaymentsPublisher {
    public void publishPayments(Envelope envelope, CaseDetails caseDetails, boolean isExceptionRecord) {
        if (envelope.payments != null && !envelope.payments.isEmpty()) {
            PaymentsData paymentsData = new PaymentsData(
                Long.toString(caseDetails.getId()),
                envelope.jurisdiction,
                envelope.poBox,
                isExceptionRecord,
                envelope.payments.stream().map(payment -> payment.documentControlNumber).collect(toList())
            );
        }
    }
}
