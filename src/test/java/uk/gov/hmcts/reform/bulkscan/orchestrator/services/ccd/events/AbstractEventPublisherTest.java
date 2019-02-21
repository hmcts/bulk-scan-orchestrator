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
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CcdAuthenticator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CcdAuthenticatorFactory;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;
import uk.gov.hmcts.reform.ccd.client.CoreCaseDataApi;
import uk.gov.hmcts.reform.ccd.client.model.CaseDataContent;
import uk.gov.hmcts.reform.ccd.client.model.Event;
import uk.gov.hmcts.reform.ccd.client.model.StartEventResponse;
import uk.gov.hmcts.reform.idam.client.models.UserDetails;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class AbstractEventPublisherTest {

    private static final String EVENT_TYPE_ID = "test";

    private static final String EVENT_SUMMARY = "test summary";

    private static final Envelope ENVELOPE = SampleData.envelope(1);

    @InjectMocks
    private AbstractEventPublisher eventPublisher = new AbstractEventPublisher() {

        @Override
        CaseData buildCaseData(StartEventResponse eventResponse, Envelope envelope) {
            return null;
        }

        @Override
        String getEventTypeId() {
            return EVENT_TYPE_ID;
        }

        @Override
        String getEventSummary() {
            return EVENT_SUMMARY;
        }
    };

    @Mock
    private CoreCaseDataApi ccdApi;

    @Mock
    private CcdAuthenticatorFactory authenticatorFactory;

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
        given(authenticatorFactory.createForJurisdiction(ENVELOPE.jurisdiction)).willReturn(authenticator);
        given(ccdApi.startEventForCaseWorker(any(), any(), any(), any(), any(), any(), any()))
            .willReturn(StartEventResponse.builder().token("event token").build());

        // when
        eventPublisher.publish(ENVELOPE, SampleData.BULK_SCANNED_CASE_TYPE);

        //then
        verify(ccdApi).startEventForCaseWorker(
            authenticator.getUserToken(),
            authenticator.getServiceToken(),
            authenticator.getUserDetails().getId(),
            ENVELOPE.jurisdiction,
            SampleData.BULK_SCANNED_CASE_TYPE,
            ENVELOPE.caseRef,
            EVENT_TYPE_ID
        );

        // and
        verify(ccdApi).submitEventForCaseWorker(
            authenticator.getUserToken(),
            authenticator.getServiceToken(),
            authenticator.getUserDetails().getId(),
            ENVELOPE.jurisdiction,
            SampleData.BULK_SCANNED_CASE_TYPE,
            ENVELOPE.caseRef,
            true,
            CaseDataContent
                .builder()
                .event(Event
                    .builder()
                    .id(EVENT_TYPE_ID)
                    .summary(EVENT_SUMMARY)
                    .build()
                )
                .data(null)
                .eventToken("event token")
                .ignoreWarning(false)
                .build()
        );
    }
}
