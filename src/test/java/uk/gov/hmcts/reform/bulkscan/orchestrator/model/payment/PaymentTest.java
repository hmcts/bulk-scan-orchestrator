package uk.gov.hmcts.reform.bulkscan.orchestrator.model.payment;

import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.bulkscan.orchestrator.entity.PaymentData;

import java.time.Instant;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

public class PaymentTest {
    private static final String envelope_id = "envelope_id";
    private static final String ccdReference = "ccdReference";
    private static final String jurisdiction = "jurisdiction";
    private static final String service = "service";
    private static final String poBox = "poBox";
    private static final boolean isExceptionRecord = false;
    private static final String status = "status";
    private static final PaymentData paymentData = new PaymentData();


    @Test
    void testCreationWhenContactTypeIsSet() {
        final uk.gov.hmcts.reform.bulkscan.orchestrator.entity.Payment paymentEntity = new uk.gov.hmcts.reform.bulkscan.orchestrator.entity.Payment(
            Instant.now(),
            envelope_id,
            ccdReference,
            jurisdiction,
            service,
            poBox,
            status,
            isExceptionRecord,
            Collections.singletonList(paymentData)
        );
        Payment payment = new Payment(paymentEntity);

        assertThat(payment.getEnvelopeId()).isEqualTo(envelope_id);
        assertThat(payment.getCcdReference()).isEqualTo(ccdReference);
        assertThat(payment.getJurisdiction()).isEqualTo(jurisdiction);
        assertThat(payment.getService()).isEqualTo(service);
        assertThat(payment.getPoBox()).isEqualTo(poBox);
        assertThat(payment.getStatus()).isEqualTo(status);
    }

}


