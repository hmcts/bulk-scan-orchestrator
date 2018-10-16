package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.events;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Classification;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.envelopeJson;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.objectMapper;

@RunWith(MockitoJUnitRunner.class)
public class EventPublisherContainerTest {

    @Mock
    private AttachDocsToSupplementaryEvidence attachDocsToSupplementaryEvidence;

    @Mock
    private CreateExceptionRecord createExceptionRecord;

    private EventPublisherContainer eventPublisherContainer;

    @Before
    public void setUp() {
        eventPublisherContainer = new EventPublisherContainer(
            attachDocsToSupplementaryEvidence,
            createExceptionRecord
        );
    }

    @Test
    public void should_get_AttachDocsToSupplementaryEvidence_event_publisher() throws IOException {
        // when
        EventPublisher eventPublisher = eventPublisherContainer.getPublisher(
            objectMapper.readValue(
                envelopeJson(Classification.SUPPLEMENTARY_EVIDENCE),
                Envelope.class
            ),
            mock(CaseDetails.class)
        );

        // then
        assertThat(eventPublisher).isInstanceOf(attachDocsToSupplementaryEvidence.getClass());
    }

    @Test
    public void should_get_CreateExceptionRecord_event_publisher_when_case_not_found() throws IOException {
        // when
        EventPublisher eventPublisher = eventPublisherContainer.getPublisher(
            objectMapper.readValue(
                envelopeJson(Classification.SUPPLEMENTARY_EVIDENCE),
                Envelope.class
            ),
            null
        );

        // then
        assertThat(eventPublisher).isInstanceOf(createExceptionRecord.getClass());
    }

    @Test
    public void should_get_CreateExceptionRecord_event_publisher() throws IOException {
        // when
        EventPublisher eventPublisher = eventPublisherContainer.getPublisher(
            objectMapper.readValue(
                envelopeJson(Classification.EXCEPTION),
                Envelope.class
            ),
            null
        );

        // then
        assertThat(eventPublisher).isInstanceOf(createExceptionRecord.getClass());
    }
}
