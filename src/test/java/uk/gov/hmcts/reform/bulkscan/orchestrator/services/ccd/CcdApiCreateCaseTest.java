package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import feign.FeignException;
import feign.Request;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.config.ServiceConfigProvider;
import uk.gov.hmcts.reform.ccd.client.CoreCaseDataApi;
import uk.gov.hmcts.reform.ccd.client.model.CaseDataContent;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.ccd.client.model.StartEventResponse;
import uk.gov.hmcts.reform.idam.client.models.UserDetails;

import java.util.function.Function;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
public class CcdApiCreateCaseTest {

    @Mock
    private CoreCaseDataApi feignCcdApi;

    @Mock
    private CcdAuthenticatorFactory authenticatorFactory;

    @Mock
    private ServiceConfigProvider serviceConfigProvider;

    @Mock
    private Function<StartEventResponse, CaseDataContent> caseDataContentBuilder;

    private CcdApi ccdApi;

    @BeforeEach
    void setUp() {
        ccdApi = new CcdApi(feignCcdApi, authenticatorFactory, serviceConfigProvider);
    }

    @Test
    void createCase_should_start_and_submit_event_with_right_data() {
        // given
        var caseDataContent = CaseDataContent.builder().build();
        given(caseDataContentBuilder.apply(any())).willReturn(caseDataContent);

        var ccdAuthenticator = sampleCcdAuthenticator();
        var jurisdiction = "jurisdiction1";
        given(authenticatorFactory.createForJurisdiction(jurisdiction)).willReturn(ccdAuthenticator);

        var startEventResponse = setupStartForCaseworker();
        var expectedCaseId = setupSubmitForCaseworker();
        var caseTypeId = "caseTypeId1";
        var eventId = "eventId1";

        // when
        long caseId = ccdApi.createCase(jurisdiction, caseTypeId, eventId, caseDataContentBuilder, "logContext1");

        // then
        assertThat(caseId).isEqualTo(expectedCaseId);

        verify(feignCcdApi).startForCaseworker(
            ccdAuthenticator.getUserToken(),
            ccdAuthenticator.getServiceToken(),
            ccdAuthenticator.getUserDetails().getId(),
            jurisdiction,
            caseTypeId,
            eventId
        );

        verify(feignCcdApi).submitForCaseworker(
            ccdAuthenticator.getUserToken(),
            ccdAuthenticator.getServiceToken(),
            ccdAuthenticator.getUserDetails().getId(),
            jurisdiction,
            caseTypeId,
            true,
            caseDataContent
        );

        verify(caseDataContentBuilder).apply(startEventResponse);
    }

    @Test
    void createCase_should_not_submit_event_when_start_event_fails() {
        // given
        var expectedException = new FeignException.BadRequest("test exception", mock(Request.class), null);

        willThrow(expectedException)
            .given(feignCcdApi)
            .startForCaseworker(any(), any(), any(), any(), any(), any());

        var ccdAuthenticator = sampleCcdAuthenticator();
        given(authenticatorFactory.createForJurisdiction(any())).willReturn(ccdAuthenticator);

        // when
        assertThatThrownBy(() ->
            ccdApi.createCase("jurisdiction1", "caseTypeId1", "eventId1", caseDataContentBuilder, "logContext1")
        )
            .isSameAs(expectedException);

        verify(feignCcdApi, never()).submitForCaseworker(any(), any(), any(), any(), any(), anyBoolean(), any());
    }

    @Test
    void createCase_should_rethrow_exception_when_submitting_event_fails() {
        // given
        var ccdAuthenticator = sampleCcdAuthenticator();
        given(authenticatorFactory.createForJurisdiction(any())).willReturn(ccdAuthenticator);

        var expectedException = new FeignException.BadRequest("test exception", mock(Request.class), null);

        willThrow(expectedException)
            .given(feignCcdApi)
            .submitForCaseworker(any(), any(), any(), any(), any(), anyBoolean(), any());

        // when
        assertThatThrownBy(() ->
            ccdApi.createCase("jurisdiction1", "caseTypeId1", "eventId1", caseDataContentBuilder, "logContext1")
        )
            .isSameAs(expectedException);
    }

    @Test
    void createCase_should_not_attempt_to_create_case_when_authentication_details_retrieval_fails() {
        // given
        var expectedException = new FeignException.Unauthorized("test exception", mock(Request.class), null);
        willThrow(expectedException)
            .given(authenticatorFactory)
            .createForJurisdiction(any());

        // when
        assertThatThrownBy(() ->
            ccdApi.createCase("jurisdiction1", "caseTypeId1", "eventId1", caseDataContentBuilder, "logContext1")
        )
            .isSameAs(expectedException);

        verifyNoInteractions(feignCcdApi);
    }

    private StartEventResponse setupStartForCaseworker() {
        var resultingStartEventResponse = StartEventResponse.builder().build();

        given(feignCcdApi.startForCaseworker(any(), any(), any(), any(), any(), any()))
            .willReturn(resultingStartEventResponse);

        return resultingStartEventResponse;
    }

    private long setupSubmitForCaseworker() {
        var resultingCaseId = 1234L;

        given(feignCcdApi.submitForCaseworker(any(), any(), any(), any(), any(), anyBoolean(), any()))
            .willReturn(CaseDetails.builder().id(resultingCaseId).build());

        return resultingCaseId;
    }

    private CcdAuthenticator sampleCcdAuthenticator() {
        return new CcdAuthenticator(
            () -> "serviceToken1",
            new UserDetails(
                "userId1",
                "email1",
                "forename1",
                "surname1",
                asList("role1", "role2")
            ),
            "userToken1"
        );
    }
}
