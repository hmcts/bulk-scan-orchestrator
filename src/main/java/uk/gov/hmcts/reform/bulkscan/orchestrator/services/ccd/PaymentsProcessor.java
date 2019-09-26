package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.IPaymentsPublisher;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.PaymentData;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.PaymentsData;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import static java.util.stream.Collectors.toList;

@Component
public class PaymentsProcessor {
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
        }
    }
}
