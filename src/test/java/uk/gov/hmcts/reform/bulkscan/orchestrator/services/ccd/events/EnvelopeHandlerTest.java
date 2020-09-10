package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.events;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CaseFinder;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.PaymentsProcessor;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Envelope;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.processedenvelopes.EnvelopeProcessingResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.CASE_ID;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.CASE_REF;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.JURSIDICTION;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.envelope;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification.EXCEPTION;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification.SUPPLEMENTARY_EVIDENCE_WITH_OCR;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.processedenvelopes.EnvelopeCcdAction.EXCEPTION_RECORD;

@ExtendWith(MockitoExtension.class)
class EnvelopeHandlerTest {

    @Mock private AttachDocsToSupplementaryEvidence attachDocsToSupplementaryEvidence;
    @Mock private CreateExceptionRecord createExceptionRecord;
    @Mock private CaseFinder caseFinder;
    @Mock private PaymentsProcessor paymentsProcessor;
    @Mock private NewApplicationHandler newApplicationHandler;
    @Mock private SupplementaryEvidenceHandler supplementaryEvidenceHandler;

    private EnvelopeHandler envelopeHandler;

    @BeforeEach
    void setUp() {
        envelopeHandler = new EnvelopeHandler(
            createExceptionRecord,
            paymentsProcessor,
            supplementaryEvidenceHandler,
            newApplicationHandler
        );
    }

    @AfterEach
    void tearDown() {
        verifyNoMoreInteractions(
            attachDocsToSupplementaryEvidence,
            createExceptionRecord,
            caseFinder,
            paymentsProcessor
        );
    }

    @Test
    void should_call_CreateExceptionRecord_for_exception_classification() {
        // given
        Envelope envelope = envelope(EXCEPTION, JURSIDICTION, CASE_REF);
        given(createExceptionRecord.tryCreateFrom(envelope)).willReturn(CASE_ID);

        // when
        EnvelopeProcessingResult envelopeProcessingResult = envelopeHandler.handleEnvelope(envelope, 0);

        // then
        assertThat(EXCEPTION_RECORD).isEqualTo(envelopeProcessingResult.envelopeCcdAction);
        assertThat(CASE_ID).isEqualTo(envelopeProcessingResult.ccdId);

        verify(createExceptionRecord).tryCreateFrom(envelope);
        verify(paymentsProcessor).createPayments(envelope, CASE_ID, true);
    }

    @Test
    void should_call_CreateExceptionRecord_for_supplementary_evidence_with_ocr_classification() {
        // given
        Envelope envelope = envelope(SUPPLEMENTARY_EVIDENCE_WITH_OCR, JURSIDICTION, CASE_REF);
        given(createExceptionRecord.tryCreateFrom(envelope)).willReturn(CASE_ID);

        // when
        EnvelopeProcessingResult envelopeProcessingResult = envelopeHandler.handleEnvelope(envelope, 0);

        // then
        assertThat(EXCEPTION_RECORD).isEqualTo(envelopeProcessingResult.envelopeCcdAction);
        assertThat(CASE_ID).isEqualTo(envelopeProcessingResult.ccdId);

        verify(createExceptionRecord).tryCreateFrom(envelope);
        verify(paymentsProcessor).createPayments(envelope, CASE_ID, true);
    }
}
