package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.IPaymentsPublisher;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.PaymentData;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.PaymentsData;

import static java.util.stream.Collectors.toList;

@Service
public class PaymentsProcessor {
    private static final Logger LOG = LoggerFactory.getLogger(PaymentsProcessor.class);

    private final IPaymentsPublisher paymentsPublisher;

    public PaymentsProcessor(
        IPaymentsPublisher paymentsPublisher
    ) {
        this.paymentsPublisher = paymentsPublisher;
    }

    public void processPayments(Envelope envelope, Long ccdId, boolean isExceptionRecord) {
        if (envelope.payments != null && !envelope.payments.isEmpty()) {
            PaymentsData paymentsData = new PaymentsData(
                Long.toString(ccdId),
                envelope.jurisdiction,
                envelope.poBox,
                isExceptionRecord,
                envelope.payments.stream()
                    .map(payment -> new PaymentData(payment.documentControlNumber))
                    .collect(toList())
            );

            LOG.info("Started processing payments for case with CCD reference {}", paymentsData.ccdReference);
            paymentsPublisher.publishPayments(paymentsData);
            LOG.info("Finished processing payments for case with CCD reference {}", paymentsData.ccdReference);
        }
    }
}
