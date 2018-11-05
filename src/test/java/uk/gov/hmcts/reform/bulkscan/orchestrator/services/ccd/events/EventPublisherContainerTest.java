package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.events;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CaseRetriever;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Classification;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.CASE_REF;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.JURSIDICTION;

@RunWith(MockitoJUnitRunner.class)
public class EventPublisherContainerTest {

    @Mock
    private AttachDocsToSupplementaryEvidence attachDocsToSupplementaryEvidence;

    @Mock
    private CreateExceptionRecord createExceptionRecord;

    @Mock
    private CaseRetriever caseRetriever;

    private EventPublisherContainer eventPublisherContainer;

    @Before
    public void setUp() {
        eventPublisherContainer = new EventPublisherContainer(
            attachDocsToSupplementaryEvidence,
            createExceptionRecord
        );
    }

    @Test
    public void should_get_AttachDocsToSupplementaryEvidence_event_publisher() {
        // given
        given(caseRetriever.retrieve(JURSIDICTION, CASE_REF)).willReturn(mock(CaseDetails.class));

        // when
        DelegatePublisher eventPublisher = (DelegatePublisher) eventPublisherContainer.getPublisher(
            Classification.SUPPLEMENTARY_EVIDENCE,
            () -> caseRetriever.retrieve(JURSIDICTION, CASE_REF)
        );

        // then
        assertThat(eventPublisher.getDelegatedClass()).isInstanceOf(attachDocsToSupplementaryEvidence.getClass());
    }

    @Test
    public void should_get_CreateExceptionRecord_event_publisher_when_case_not_found() {
        // when
        EventPublisher eventPublisher = eventPublisherContainer.getPublisher(
            Classification.SUPPLEMENTARY_EVIDENCE,
            () -> caseRetriever.retrieve(JURSIDICTION, CASE_REF)
        );

        // then
        assertThat(eventPublisher).isInstanceOf(createExceptionRecord.getClass());

        // and
        verify(caseRetriever).retrieve(JURSIDICTION, CASE_REF);
    }

    @Test
    public void should_get_CreateExceptionRecord_event_publisher() {
        // when
        EventPublisher eventPublisher = eventPublisherContainer.getPublisher(
            Classification.EXCEPTION,
            () -> caseRetriever.retrieve(JURSIDICTION, CASE_REF)
        );

        // then
        assertThat(eventPublisher).isInstanceOf(createExceptionRecord.getClass());

        // and
        verify(caseRetriever, never()).retrieve(JURSIDICTION, CASE_REF);
    }

    @Test
    public void should_get_Void_event_publisher_for_not_implemented_classification() {
        // when
        DelegatePublisher eventPublisher = (DelegatePublisher) eventPublisherContainer.getPublisher(
            Classification.NEW_APPLICATION,
            () -> caseRetriever.retrieve(JURSIDICTION, CASE_REF)
        );

        // then
        assertThat(eventPublisher.getDelegatedClass()).isNull();

        // and
        verify(caseRetriever, never()).retrieve(JURSIDICTION, CASE_REF);
    }
}
