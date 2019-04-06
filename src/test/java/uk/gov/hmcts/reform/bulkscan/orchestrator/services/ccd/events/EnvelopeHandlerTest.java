package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.events;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CaseRetriever;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.CASE_REF;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.JURSIDICTION;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.envelope;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Classification.EXCEPTION;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Classification.NEW_APPLICATION;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Classification.SUPPLEMENTARY_EVIDENCE;

@SuppressWarnings("checkstyle:LineLength")
@ExtendWith(MockitoExtension.class)
public class EnvelopeHandlerTest {

    @Mock
    private AttachDocsToSupplementaryEvidence attachDocsToSupplementaryEvidence;

    @Mock
    private CreateExceptionRecord createExceptionRecord;

    @Mock
    private CaseRetriever caseRetriever;

    @Mock
    private CaseDetails caseDetails;

    private EnvelopeHandler envelopeHandler;

    @BeforeEach
    public void setUp() {
        envelopeHandler = new EnvelopeHandler(
            attachDocsToSupplementaryEvidence,
            createExceptionRecord,
            caseRetriever
        );
    }

    @Test
    public void should_call_AttachDocsToSupplementaryEvidence_for_supplementary_evidence_classification_when_case_exists() {
        // given
        given(caseRetriever.retrieve(JURSIDICTION, CASE_REF)).willReturn(caseDetails);
        Envelope envelope = envelope(SUPPLEMENTARY_EVIDENCE, JURSIDICTION, CASE_REF);

        // when
        envelopeHandler.handleEnvelope(envelope);

        // then
        verify(attachDocsToSupplementaryEvidence).publish(envelope, caseDetails);
    }

    @Test
    public void should_call_CreateExceptionRecord_for_supplementary_evidence_classification_when_case_does_not_exist() {
        // given
        given(caseRetriever.retrieve(JURSIDICTION, CASE_REF)).willReturn(null); // case not found
        Envelope envelope = envelope(SUPPLEMENTARY_EVIDENCE, JURSIDICTION, CASE_REF);

        // when
        envelopeHandler.handleEnvelope(envelope);

        // then
        verify(this.createExceptionRecord).publish(envelope);
    }

    @Test
    public void should_call_CreateExceptionRecord_for_exception_classification() {
        // given
        Envelope envelope = envelope(EXCEPTION, JURSIDICTION, CASE_REF);

        // when
        envelopeHandler.handleEnvelope(envelope);

        // then
        verify(this.createExceptionRecord).publish(envelope);
        verify(caseRetriever, never()).retrieve(JURSIDICTION, CASE_REF);
    }

    @Test
    public void should_call_CreateExceptionRecord_for_new_application_classification() {
        // given
        Envelope envelope = envelope(NEW_APPLICATION, JURSIDICTION, CASE_REF);

        // when
        envelopeHandler.handleEnvelope(envelope);

        // then
        verify(this.createExceptionRecord).publish(envelope);
        verify(caseRetriever, never()).retrieve(any(), any());
    }

}
