package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.events;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Classification.EXCEPTION;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Classification.NEW_APPLICATION;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Classification.SUPPLEMENTARY_EVIDENCE;

@SuppressWarnings("checkstyle:LineLength")
@RunWith(MockitoJUnitRunner.class)
public class EventPublisherContainerTest {

    @Mock
    private AttachDocsToSupplementaryEvidence attachDocsToSupplementaryEvidence;

    @Mock
    private CreateExceptionRecord createExceptionRecord;

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

        // when
        EventPublisher publisher = eventPublisherContainer.getPublisher(
            SUPPLEMENTARY_EVIDENCE,
            () -> caseDetails
        );
        publisher.publish(envelope);

        // then
        verify(attachDocsToSupplementaryEvidence).publish(envelope, "someCaseTypeId");
    }

    @Test
    public void should_call_CreateExceptionRecord_for_supplementary_evidence_classification_when_case_does_not_exist() {
        // when
        EventPublisher publisher = eventPublisherContainer.getPublisher(
            SUPPLEMENTARY_EVIDENCE,
            () -> null
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
            () -> caseDetails
        );
        eventPublisher.publish(envelope);

        // then
        verify(this.createExceptionRecord).publish(envelope);
    }

    @Test
    public void should_call_CreateExceptionRecord_for_new_application_classification() {
        // when
        EventPublisher eventPublisher = eventPublisherContainer.getPublisher(
            NEW_APPLICATION,
            () -> caseDetails
        );
        eventPublisher.publish(envelope);

        // then
        verify(this.createExceptionRecord).publish(envelope);
    }

}
