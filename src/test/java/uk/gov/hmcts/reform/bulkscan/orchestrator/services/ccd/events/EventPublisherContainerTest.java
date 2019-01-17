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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
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
        EventPublisher eventPublisher = getEventPublisher(SUPPLEMENTARY_EVIDENCE);

        // then
        assertThat(eventPublisher).isInstanceOf(attachDocsToSupplementaryEvidence.getClass());
    }

    @Test
    public void should_get_CreateExceptionRecord_event_publisher_when_case_not_found() {
        // when
        EventPublisher eventPublisher = getEventPublisher(SUPPLEMENTARY_EVIDENCE);

        // then
        assertThat(eventPublisher).isInstanceOf(createExceptionRecord.getClass());

        // and
        verify(caseRetriever).retrieve(JURSIDICTION, CASE_REF);
    }

    @Test
    public void should_get_CreateExceptionRecord_event_publisher_for_exception() {
        // when
        EventPublisher eventPublisher = getEventPublisher(EXCEPTION);

        // then
        assertThat(eventPublisher).isInstanceOf(createExceptionRecord.getClass());

        // and
        verify(caseRetriever, never()).retrieve(JURSIDICTION, CASE_REF);
    }

    @Test
    public void should_get_CreateExceptionRecord_event_publisher_for_new_application() {
        // when
        EventPublisher eventPublisher = getEventPublisher(NEW_APPLICATION);

        // then
        assertThat(eventPublisher).isInstanceOf(CreateExceptionRecord.class);

        // and
        verify(caseRetriever, never()).retrieve(any(), any());
    }

    private EventPublisher getEventPublisher(Classification classification) {
        return eventPublisherContainer.getPublisher(
            classification,
            () -> caseRetriever.retrieve(JURSIDICTION, CASE_REF)
        );
    }
}
