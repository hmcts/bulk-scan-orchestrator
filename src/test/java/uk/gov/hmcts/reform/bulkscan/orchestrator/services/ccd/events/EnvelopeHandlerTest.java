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
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.processedenvelopes.EnvelopeProcessResult;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.CASE_ID;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.CASE_REF;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.JURSIDICTION;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.envelope;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification.EXCEPTION;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification.NEW_APPLICATION;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification.SUPPLEMENTARY_EVIDENCE;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification.SUPPLEMENTARY_EVIDENCE_WITH_OCR;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.processedenvelopes.EnvelopeCcdAction.AUTO_ATTACHED_CASE;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.processedenvelopes.EnvelopeCcdAction.EXCEPTION_RECORD;

@ExtendWith(MockitoExtension.class)
class EnvelopeHandlerTest {

    @Mock private AttachDocsToSupplementaryEvidence attachDocsToSupplementaryEvidence;
    @Mock private CreateExceptionRecord createExceptionRecord;
    @Mock private CaseFinder caseFinder;
    @Mock private CaseDetails caseDetails;
    @Mock private PaymentsProcessor paymentsProcessor;

    private EnvelopeHandler envelopeHandler;

    @BeforeEach
    void setUp() {
        envelopeHandler = new EnvelopeHandler(
            attachDocsToSupplementaryEvidence,
            createExceptionRecord,
            caseFinder,
            paymentsProcessor
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
    void should_call_AttachDocsToSupplementaryEvidence_for_supplementary_evidence_classification_when_case_exists() {
        // given
        Envelope envelope = envelope(SUPPLEMENTARY_EVIDENCE, JURSIDICTION, CASE_REF);
        given(caseFinder.findCase(envelope)).willReturn(Optional.of(caseDetails));
        given(attachDocsToSupplementaryEvidence.attach(envelope, caseDetails)).willReturn(true);
        Long ccdId = 321394383L;
        given(caseDetails.getId()).willReturn(ccdId);

        // when
        EnvelopeProcessResult envelopeProcessResult = envelopeHandler.handleEnvelope(envelope);

        // then
        assertThat(AUTO_ATTACHED_CASE).isEqualTo(envelopeProcessResult.processedCcdType);
        assertThat(ccdId).isEqualTo(envelopeProcessResult.ccdId);

        verify(attachDocsToSupplementaryEvidence).attach(envelope, caseDetails);
        verify(paymentsProcessor).createPayments(envelope, caseDetails.getId(), false);
    }

    @Test
    void should_call_CreateExceptionRecord_for_supplementary_evidence_classification_when_case_does_not_exist() {
        // given
        Envelope envelope = envelope(SUPPLEMENTARY_EVIDENCE, JURSIDICTION, CASE_REF);
        given(caseFinder.findCase(envelope)).willReturn(Optional.empty()); // case not found
        given(createExceptionRecord.tryCreateFrom(envelope)).willReturn(CASE_ID);

        // when
        EnvelopeProcessResult envelopeProcessResult = envelopeHandler.handleEnvelope(envelope);

        // then
        assertThat(EXCEPTION_RECORD).isEqualTo(envelopeProcessResult.processedCcdType);
        assertThat(CASE_ID).isEqualTo(envelopeProcessResult.ccdId);

        verify(createExceptionRecord).tryCreateFrom(envelope);
        verify(paymentsProcessor).createPayments(envelope, CASE_ID, true);
    }

    @Test
    void should_call_CreateExceptionRecord_for_exception_classification() {
        // given
        Envelope envelope = envelope(EXCEPTION, JURSIDICTION, CASE_REF);
        given(createExceptionRecord.tryCreateFrom(envelope)).willReturn(CASE_ID);

        // when
        EnvelopeProcessResult envelopeProcessResult = envelopeHandler.handleEnvelope(envelope);

        // then
        assertThat(EXCEPTION_RECORD).isEqualTo(envelopeProcessResult.processedCcdType);
        assertThat(CASE_ID).isEqualTo(envelopeProcessResult.ccdId);

        verify(createExceptionRecord).tryCreateFrom(envelope);
        verify(paymentsProcessor).createPayments(envelope, CASE_ID, true);
    }

    @Test
    void should_call_CreateExceptionRecord_for_new_application_classification() {
        // given
        Envelope envelope = envelope(NEW_APPLICATION, JURSIDICTION, CASE_REF);
        given(createExceptionRecord.tryCreateFrom(envelope)).willReturn(CASE_ID);

        // when
        EnvelopeProcessResult envelopeProcessResult = envelopeHandler.handleEnvelope(envelope);

        // then
        assertThat(EXCEPTION_RECORD).isEqualTo(envelopeProcessResult.processedCcdType);
        assertThat(CASE_ID).isEqualTo(envelopeProcessResult.ccdId);

        verify(createExceptionRecord).tryCreateFrom(envelope);
        verify(caseFinder, never()).findCase(any());
        verify(paymentsProcessor).createPayments(envelope, CASE_ID, true);
    }

    @Test
    void should_call_CreateExceptionRecord_for_supplementary_evidence_with_ocr_classification() {
        // given
        Envelope envelope = envelope(SUPPLEMENTARY_EVIDENCE_WITH_OCR, JURSIDICTION, CASE_REF);
        given(createExceptionRecord.tryCreateFrom(envelope)).willReturn(CASE_ID);

        // when
        EnvelopeProcessResult envelopeProcessResult = envelopeHandler.handleEnvelope(envelope);

        // then
        assertThat(EXCEPTION_RECORD).isEqualTo(envelopeProcessResult.processedCcdType);
        assertThat(CASE_ID).isEqualTo(envelopeProcessResult.ccdId);

        verify(createExceptionRecord).tryCreateFrom(envelope);
        verify(paymentsProcessor).createPayments(envelope, CASE_ID, true);
    }

    @Test
    void should_call_CreateExceptionRecord_when_documents_attachment_fails_for_supplementary_evidence() {
        // given
        Envelope envelope = envelope(SUPPLEMENTARY_EVIDENCE, JURSIDICTION, CASE_REF);
        given(caseFinder.findCase(envelope)).willReturn(Optional.of(caseDetails));
        given(attachDocsToSupplementaryEvidence.attach(envelope, caseDetails)).willReturn(false);
        given(createExceptionRecord.tryCreateFrom(envelope)).willReturn(CASE_ID);

        // when
        EnvelopeProcessResult envelopeProcessResult = envelopeHandler.handleEnvelope(envelope);

        // then
        assertThat(EXCEPTION_RECORD).isEqualTo(envelopeProcessResult.processedCcdType);
        assertThat(CASE_ID).isEqualTo(envelopeProcessResult.ccdId);

        verify(attachDocsToSupplementaryEvidence).attach(envelope, caseDetails);
        verify(createExceptionRecord).tryCreateFrom(envelope);
        verify(paymentsProcessor).createPayments(envelope, CASE_ID, true);
    }

}
