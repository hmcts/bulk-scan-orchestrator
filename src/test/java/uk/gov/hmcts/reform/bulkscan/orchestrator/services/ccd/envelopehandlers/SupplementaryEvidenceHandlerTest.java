package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.envelopehandlers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.PaymentsService;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CaseFinder;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Envelope;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.CASE_ID;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.CASE_REF;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.JURSIDICTION;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.envelope;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification.EXCEPTION;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification.SUPPLEMENTARY_EVIDENCE;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.processedenvelopes.EnvelopeCcdAction.AUTO_ATTACHED_TO_CASE;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.processedenvelopes.EnvelopeCcdAction.EXCEPTION_RECORD;

@ExtendWith(MockitoExtension.class)
class SupplementaryEvidenceHandlerTest {

    @Mock CaseFinder caseFinder;
    @Mock AttachDocsToSupplementaryEvidence evidenceAttacher;
    @Mock PaymentsService paymentsService;
    @Mock CreateExceptionRecord exceptionRecordCreator;

    @Mock CaseDetails caseDetails;

    SupplementaryEvidenceHandler handler;

    @BeforeEach
    void setUp() {
        handler = new SupplementaryEvidenceHandler(
            caseFinder,
            evidenceAttacher,
            exceptionRecordCreator,
            paymentsService
        );
    }

    @Test
    void should_call_AttachDocsToSupplementaryEvidence_when_case_exists() {
        // given
        Envelope envelope = envelope(SUPPLEMENTARY_EVIDENCE, JURSIDICTION, CASE_REF);
        given(caseFinder.findCase(envelope)).willReturn(Optional.of(caseDetails));
        given(evidenceAttacher.attach(envelope, caseDetails)).willReturn(true);
        Long ccdId = 321394383L;
        given(caseDetails.getId()).willReturn(ccdId);

        // when
        var result = handler.handle(envelope);

        // then
        assertThat(result.envelopeCcdAction).isEqualTo(AUTO_ATTACHED_TO_CASE);
        assertThat(result.ccdId).isEqualTo(ccdId);

        verify(evidenceAttacher).attach(envelope, caseDetails);
        verify(paymentsService).createNewPayment(envelope, caseDetails.getId(), false);
    }

    @Test
    void should_call_CreateExceptionRecord_when_case_does_not_exist() {
        // given
        Envelope envelope = envelope(SUPPLEMENTARY_EVIDENCE, JURSIDICTION, CASE_REF);
        given(caseFinder.findCase(envelope)).willReturn(Optional.empty()); // case not found
        given(exceptionRecordCreator.tryCreateFrom(envelope)).willReturn(CASE_ID);

        // when
        var result = handler.handle(envelope);

        // then
        assertThat(result.envelopeCcdAction).isEqualTo(EXCEPTION_RECORD);
        assertThat(result.ccdId).isEqualTo(CASE_ID);

        verify(exceptionRecordCreator).tryCreateFrom(envelope);
        verify(paymentsService).createNewPayment(envelope, CASE_ID, true);
    }

    @Test
    void should_call_CreateExceptionRecord_when_documents_attachment_fails_for_supplementary_evidence() {
        // given
        Envelope envelope = envelope(SUPPLEMENTARY_EVIDENCE, JURSIDICTION, CASE_REF);
        given(caseFinder.findCase(envelope)).willReturn(Optional.of(caseDetails));
        given(evidenceAttacher.attach(envelope, caseDetails)).willReturn(false);
        given(exceptionRecordCreator.tryCreateFrom(envelope)).willReturn(CASE_ID);

        // when
        var result = handler.handle(envelope);

        // then
        assertThat(result.envelopeCcdAction).isEqualTo(EXCEPTION_RECORD);
        assertThat(result.ccdId).isEqualTo(CASE_ID);

        verify(evidenceAttacher).attach(envelope, caseDetails);
        verify(exceptionRecordCreator).tryCreateFrom(envelope);
        verify(paymentsService).createNewPayment(envelope, CASE_ID, true);
    }

    @Test
    void should_throw_an_exception_if_envelope_classification_is_not_correct() {
        // given
        Envelope envelope = envelope(EXCEPTION, JURSIDICTION, CASE_REF);

        // when
        var exc = catchThrowable(() -> handler.handle(envelope));

        // then
        assertThat(exc)
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Envelope classification")
            .hasMessageContaining(SUPPLEMENTARY_EVIDENCE.toString());
    }
}
