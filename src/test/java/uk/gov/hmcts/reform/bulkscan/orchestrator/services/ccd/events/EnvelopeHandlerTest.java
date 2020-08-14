package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.events;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.AutoCaseCreator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CaseCreationException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CaseFinder;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.PaymentsProcessor;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Envelope;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.processedenvelopes.EnvelopeCcdAction;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.processedenvelopes.EnvelopeProcessingResult;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.CASE_ID;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.CASE_REF;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.JURSIDICTION;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.envelope;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CaseCreationResult.caseCreated;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CaseCreationResult.potentiallyRecoverableFailure;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CaseCreationResult.unrecoverableFailure;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification.EXCEPTION;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification.NEW_APPLICATION;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification.SUPPLEMENTARY_EVIDENCE;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification.SUPPLEMENTARY_EVIDENCE_WITH_OCR;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.processedenvelopes.EnvelopeCcdAction.AUTO_ATTACHED_TO_CASE;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.processedenvelopes.EnvelopeCcdAction.EXCEPTION_RECORD;

@ExtendWith(MockitoExtension.class)
class EnvelopeHandlerTest {

    private static final int MAX_ALLOWED_RETRIES_FOR_RECOVERABLE_FAILURES = 2;

    @Mock private AttachDocsToSupplementaryEvidence attachDocsToSupplementaryEvidence;
    @Mock private CreateExceptionRecord createExceptionRecord;
    @Mock private CaseFinder caseFinder;
    @Mock private CaseDetails caseDetails;
    @Mock private PaymentsProcessor paymentsProcessor;
    @Mock private AutoCaseCreator autoCaseCreator;

    private EnvelopeHandler envelopeHandler;

    @BeforeEach
    void setUp() {
        envelopeHandler = new EnvelopeHandler(
            attachDocsToSupplementaryEvidence,
            createExceptionRecord,
            caseFinder,
            paymentsProcessor,
            autoCaseCreator
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
        EnvelopeProcessingResult envelopeProcessingResult = envelopeHandler.handleEnvelope(envelope, 0);

        // then
        assertThat(AUTO_ATTACHED_TO_CASE).isEqualTo(envelopeProcessingResult.envelopeCcdAction);
        assertThat(ccdId).isEqualTo(envelopeProcessingResult.ccdId);

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
        EnvelopeProcessingResult envelopeProcessingResult = envelopeHandler.handleEnvelope(envelope, 0);

        // then
        assertThat(EXCEPTION_RECORD).isEqualTo(envelopeProcessingResult.envelopeCcdAction);
        assertThat(CASE_ID).isEqualTo(envelopeProcessingResult.ccdId);

        verify(createExceptionRecord).tryCreateFrom(envelope);
        verify(paymentsProcessor).createPayments(envelope, CASE_ID, true);
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
    void should_not_create_exception_record_for_new_application_when_case_creation_successful() {
        // given
        long caseId = 1234L;
        Envelope envelope = envelope(NEW_APPLICATION, JURSIDICTION, CASE_REF);
        given(autoCaseCreator.createCase(envelope)).willReturn(caseCreated(caseId));

        // when
        EnvelopeProcessingResult envelopeProcessingResult = envelopeHandler.handleEnvelope(envelope, 0);

        // then
        assertThat(envelopeProcessingResult.envelopeCcdAction).isEqualTo(EnvelopeCcdAction.CASE_CREATED);
        assertThat(envelopeProcessingResult.ccdId).isEqualTo(caseId);

        verify(autoCaseCreator).createCase(envelope);
        verify(createExceptionRecord, never()).tryCreateFrom(any());
        verify(paymentsProcessor).createPayments(envelope, caseId, false);
    }

    @Test
    void should_create_exception_record_for_new_application_when_unrecoverable_failure() {
        // given
        Envelope envelope = envelope(NEW_APPLICATION, JURSIDICTION, CASE_REF);
        given(autoCaseCreator.createCase(envelope)).willReturn(unrecoverableFailure());
        given(createExceptionRecord.tryCreateFrom(envelope)).willReturn(CASE_ID);

        // when
        EnvelopeProcessingResult envelopeProcessingResult = envelopeHandler.handleEnvelope(envelope, 0);

        // then
        assertThat(EXCEPTION_RECORD).isEqualTo(envelopeProcessingResult.envelopeCcdAction);
        assertThat(CASE_ID).isEqualTo(envelopeProcessingResult.ccdId);

        verify(autoCaseCreator).createCase(envelope);
        verify(createExceptionRecord).tryCreateFrom(envelope);
        verify(caseFinder, never()).findCase(any());
        verify(paymentsProcessor).createPayments(envelope, CASE_ID, true);
    }

    @Test
    void should_throw_exception_record_for_new_application_when_recoverable_failure() {
        Envelope envelope = envelope(NEW_APPLICATION, JURSIDICTION, CASE_REF);
        given(autoCaseCreator.createCase(envelope)).willReturn(potentiallyRecoverableFailure());

        assertThatThrownBy(() -> envelopeHandler.handleEnvelope(envelope, 0))
            .isInstanceOf(CaseCreationException.class)
            .hasMessage("Case creation failed due to a potentially recoverable error");

        verify(autoCaseCreator).createCase(envelope);
    }

    @Test
    void should_create_exception_record_for_new_application_when_too_many_deliveries() {
        // given
        Envelope envelope = envelope(NEW_APPLICATION, JURSIDICTION, CASE_REF);
        given(autoCaseCreator.createCase(envelope)).willReturn(potentiallyRecoverableFailure());
        given(createExceptionRecord.tryCreateFrom(envelope)).willReturn(CASE_ID);

        // when
        EnvelopeProcessingResult envelopeProcessingResult =
            envelopeHandler.handleEnvelope(envelope, MAX_ALLOWED_RETRIES_FOR_RECOVERABLE_FAILURES);

        // then
        assertThat(EXCEPTION_RECORD).isEqualTo(envelopeProcessingResult.envelopeCcdAction);
        assertThat(CASE_ID).isEqualTo(envelopeProcessingResult.ccdId);

        verify(autoCaseCreator).createCase(envelope);
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
        EnvelopeProcessingResult envelopeProcessingResult = envelopeHandler.handleEnvelope(envelope, 0);

        // then
        assertThat(EXCEPTION_RECORD).isEqualTo(envelopeProcessingResult.envelopeCcdAction);
        assertThat(CASE_ID).isEqualTo(envelopeProcessingResult.ccdId);

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
        EnvelopeProcessingResult envelopeProcessingResult = envelopeHandler.handleEnvelope(envelope, 0);

        // then
        assertThat(EXCEPTION_RECORD).isEqualTo(envelopeProcessingResult.envelopeCcdAction);
        assertThat(CASE_ID).isEqualTo(envelopeProcessingResult.ccdId);

        verify(attachDocsToSupplementaryEvidence).attach(envelope, caseDetails);
        verify(createExceptionRecord).tryCreateFrom(envelope);
        verify(paymentsProcessor).createPayments(envelope, CASE_ID, true);
    }
}
