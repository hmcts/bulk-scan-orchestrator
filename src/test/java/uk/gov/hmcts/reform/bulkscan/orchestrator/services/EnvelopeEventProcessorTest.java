package uk.gov.hmcts.reform.bulkscan.orchestrator.services;

import com.microsoft.azure.servicebus.IMessage;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class EnvelopeEventProcessorTest {

    private static final String JURISDICTION = "SSCS";
    private static final String SOME_CASE_REF = "someCaseRef";
    private EnvelopeEventProcessor processor;

    @Mock
    private IMessage someMessage;
    @Mock
    private CcdCaseRetriever caseRetriever;
    @Mock
    private CcdAuthService authenticator;
    @Mock
    private CaseDetails theCase;
    @Mock
    private CcdAuthInfo authInfo;

    @Before
    public void before() throws Exception {
        processor = new EnvelopeEventProcessor(caseRetriever, authenticator);
        when(caseRetriever.retrieve(authInfo, JURISDICTION, SOME_CASE_REF)).thenReturn(theCase);
        given(someMessage.getBody()).willReturn(SampleData.envelopeJson().getBytes());
        given(authenticator.authenticateForJurisdiction(eq(JURISDICTION))).willReturn(authInfo);

    }

    @Test
    public void should_return_completed_future_if_everything_went_fine() {

        // when
        CompletableFuture<Void> result = processor.onMessageAsync(someMessage);

        // then
        assertThat(result.isDone()).isTrue();
        assertThat(result.isCompletedExceptionally()).isFalse();
    }

    @Test
    public void should_return_exceptionally_completed_future_if_queue_message_contains_invalid_envelope() {
        // given
        given(someMessage.getBody())
            .willReturn("foo".getBytes());

        // when
        CompletableFuture<Void> result = processor.onMessageAsync(someMessage);

        // then
        assertThat(result.isCompletedExceptionally()).isTrue();
    }

    @Test
    public void should_not_do_anything_in_notify() {
        // when
        processor.notifyException(null, null);
    }

    @Test
    public void should_return_exceptionally_completed_future_if_unknown_jurisdiction() throws Exception {
        // given
        given(someMessage.getBody()).willReturn(SampleData.envelopeJson().getBytes());
        given(authenticator.authenticateForJurisdiction(any())).willThrow(new RuntimeException());

        // when
        CompletableFuture<Void> result = processor.onMessageAsync(someMessage);

        // then
        assertThat(result.isCompletedExceptionally()).isTrue();
    }

}
