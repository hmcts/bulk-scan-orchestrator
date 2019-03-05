package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.events;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.CaseData;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CcdApi;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CcdAuthenticator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;
import uk.gov.hmcts.reform.ccd.client.model.CaseDataContent;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.ccd.client.model.Event;
import uk.gov.hmcts.reform.ccd.client.model.StartEventResponse;
import uk.gov.hmcts.reform.idam.client.models.UserDetails;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class AbstractEventPublisherTest {

    private static final Envelope ENVELOPE = SampleData.envelope(1);

    @InjectMocks
    private final TestEventPublisher eventPublisher = new TestEventPublisher();

    @Mock
    private CcdApi ccdApi;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void should_successfully_call_start_and_submit_event_calls() {
        // given
        CcdAuthenticator authenticator = new CcdAuthenticator(
            () -> "token",
            new UserDetails("id", "email", "forename", "surname", Collections.emptyList()),
            () -> "user token"
        );
        CaseDetails caseDetails = mock(CaseDetails.class);

        given(ccdApi.authenticateJurisdiction(ENVELOPE.jurisdiction)).willReturn(authenticator);
        given(ccdApi.startEvent(any(), any(), any(), any(), any()))
            .willReturn(StartEventResponse.builder().token("event token").build());
        given(ccdApi.submitEvent(any(), any(), any(), any(), any())).willReturn(caseDetails);
        given(caseDetails.getId()).willReturn(1L);

        // when
        eventPublisher.publish(ENVELOPE, SampleData.BULK_SCANNED_CASE_TYPE);

        //then
        verify(ccdApi).startEvent(
            authenticator,
            ENVELOPE.jurisdiction,
            SampleData.BULK_SCANNED_CASE_TYPE,
            ENVELOPE.caseRef,
            TestEventPublisher.EVENT_TYPE_ID
        );

        // and
        verify(ccdApi).submitEvent(
            authenticator,
            ENVELOPE.jurisdiction,
            SampleData.BULK_SCANNED_CASE_TYPE,
            ENVELOPE.caseRef,
            CaseDataContent
                .builder()
                .event(Event
                    .builder()
                    .id(TestEventPublisher.EVENT_TYPE_ID)
                    .summary(TestEventPublisher.EVENT_SUMMARY)
                    .build()
                )
                .data(null)
                .eventToken("event token")
                .ignoreWarning(false)
                .build()
        );
    }

    protected class TestEventPublisher extends AbstractEventPublisher<Envelope> {
        protected static final String EVENT_TYPE_ID = "test";
        protected static final String EVENT_SUMMARY = "test summary";

        @Override
        String getCaseReference(Envelope eventSource) {
            return eventSource.caseRef;
        }

        @Override
        protected CaseData buildCaseData(StartEventResponse eventResponse, Envelope envelope) {
            return null;
        }

        public void publish(Envelope env, String caseType) {
            publish(env, caseType, EVENT_TYPE_ID, EVENT_SUMMARY);
        }
    }
}
