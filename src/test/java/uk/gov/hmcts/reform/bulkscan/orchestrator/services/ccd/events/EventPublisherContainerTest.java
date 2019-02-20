package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.events;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CaseRetriever;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.CASE_REF;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.JURSIDICTION;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Classification.EXCEPTION;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Classification.NEW_APPLICATION;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Classification.SUPPLEMENTARY_EVIDENCE;

@RunWith(MockitoJUnitRunner.class)
public class EventPublisherContainerTest {

    @Mock
    private AttachDocsToSupplementaryEvidence attachDocsToSupplementaryEvidence;

    @Mock
    private CreateExceptionRecord createExceptionRecord;

    @Mock
    private CaseRetriever caseRetriever;

    @Mock
    private Envelope envelope;

    @Mock
    private CaseDetails caseDetails;

    private EventPublisherContainer eventPublisherContainer;

    @Before
    public void setUp() {
        eventPublisherContainer = new EventPublisherContainer(
            attachDocsToSupplementaryEvidence,
            createExceptionRecord
        );
    }

    @Test
    public void should_call_AttachDocsToSupplementaryEvidence_for_supplementary_evidence_classification_when_case_exists() {
        // given
        given(caseDetails.getCaseTypeId()).willReturn("someCaseTypeId");
        given(caseRetriever.retrieve(JURSIDICTION, CASE_REF)).willReturn(caseDetails);

        // when
        EventPublisher publisher = eventPublisherContainer.getPublisher(
            SUPPLEMENTARY_EVIDENCE,
            () -> caseRetriever.retrieve(JURSIDICTION, CASE_REF)
        );
        publisher.publish(envelope);

        // then
        verify(attachDocsToSupplementaryEvidence).publish(envelope, "someCaseTypeId");
    }

    @Test
    public void should_call_CreateExceptionRecord_for_supplementary_evidence_classification_when_case_does_not_exist() {
        // given
        given(caseRetriever.retrieve(JURSIDICTION, CASE_REF)).willReturn(null); // case not found

        // when
        EventPublisher publisher = eventPublisherContainer.getPublisher(
            SUPPLEMENTARY_EVIDENCE,
            () -> caseRetriever.retrieve(JURSIDICTION, CASE_REF)
        );
        publisher.publish(envelope);

        // then
        verify(this.createExceptionRecord).publish(envelope);
    }

    @Test
    public void should_call_CreateExceptionRecord_for_exception_classification() {
        // when
        EventPublisher eventPublisher = eventPublisherContainer.getPublisher(
            EXCEPTION,
            () -> caseRetriever.retrieve(JURSIDICTION, CASE_REF)
        );
        eventPublisher.publish(envelope);

        // then
        verify(this.createExceptionRecord).publish(envelope);

        // and
        verify(caseRetriever, never()).retrieve(JURSIDICTION, CASE_REF);
    }

    @Test
    public void should_call_CreateExceptionRecord_for_new_application_classification() {
        // when
        EventPublisher eventPublisher = eventPublisherContainer.getPublisher(
            NEW_APPLICATION,
            () -> caseRetriever.retrieve(JURSIDICTION, CASE_REF)
        );
        eventPublisher.publish(envelope);

        // then
        verify(this.createExceptionRecord).publish(envelope);

        // and
        verify(caseRetriever, never()).retrieve(any(), any());
    }

}
