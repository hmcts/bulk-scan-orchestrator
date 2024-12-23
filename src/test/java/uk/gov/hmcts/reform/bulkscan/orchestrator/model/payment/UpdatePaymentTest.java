package uk.gov.hmcts.reform.bulkscan.orchestrator.model.payment;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

public class UpdatePaymentTest {

    private static final String exceptionRecordRef = "exception_record_ref";
    private static final String newCaseRef = "new_case_ref";
    private static final String envelope_id = "envelope_id";
    private static final String jurisdiction = "jurisdiction";
    private static final String status = "status";


    @Test
    void testUpdatePaymentCreation() {
        final uk.gov.hmcts.reform.bulkscan.orchestrator.entity.UpdatePayment updatePaymentEntity =
            new uk.gov.hmcts.reform.bulkscan.orchestrator.entity.UpdatePayment(
                Instant.now(),
                exceptionRecordRef,
                newCaseRef,
                envelope_id,
                jurisdiction,
                status
            );
        uk.gov.hmcts.reform.bulkscan.orchestrator.model.payment.UpdatePayment updatePayment =
            new uk.gov.hmcts.reform.bulkscan.orchestrator.model.payment.UpdatePayment(updatePaymentEntity);

        assertThat(updatePayment.getExceptionRecordRef()).isEqualTo(exceptionRecordRef);
        assertThat(updatePayment.getNewCaseRef()).isEqualTo(newCaseRef);
        assertThat(updatePayment.getEnvelopeId()).isEqualTo(envelope_id);
        assertThat(updatePayment.getJurisdiction()).isEqualTo(jurisdiction);
        assertThat(updatePayment.getStatus()).isEqualTo(status);
    }

}
