package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.events;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.PaymentsProcessor;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Envelope;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.CASE_ID;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.CASE_REF;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.JURSIDICTION;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.envelope;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification.SUPPLEMENTARY_EVIDENCE_WITH_OCR;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.processedenvelopes.EnvelopeCcdAction.EXCEPTION_RECORD;

@ExtendWith(MockitoExtension.class)
class SupplementaryEvidenceWithOcrHandlerTest {

    @Mock CreateExceptionRecord exceptionRecordCreator;
    @Mock PaymentsProcessor paymentsProcessor;

    SupplementaryEvidenceWithOcrHandler handler;

    @BeforeEach
    void setUp() {
        handler = new SupplementaryEvidenceWithOcrHandler(exceptionRecordCreator, paymentsProcessor);
    }

    @Test
    void should_create_exception_record() {
        // given
        Envelope envelope = envelope(SUPPLEMENTARY_EVIDENCE_WITH_OCR, JURSIDICTION, CASE_REF);
        given(exceptionRecordCreator.tryCreateFrom(envelope)).willReturn(CASE_ID);

        // when
        var result = handler.handle(envelope);

        // then
        assertThat(result.envelopeCcdAction).isEqualTo(EXCEPTION_RECORD);
        assertThat(result.ccdId).isEqualTo(CASE_ID);

        verify(exceptionRecordCreator).tryCreateFrom(envelope);
        verify(paymentsProcessor).createPayments(envelope, CASE_ID, true);
    }
}
