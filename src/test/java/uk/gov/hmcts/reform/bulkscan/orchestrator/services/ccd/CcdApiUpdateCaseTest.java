package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import feign.FeignException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.config.ServiceConfigProvider;
import uk.gov.hmcts.reform.ccd.client.CoreCaseDataApi;
import uk.gov.hmcts.reform.ccd.client.model.CaseDataContent;
import uk.gov.hmcts.reform.ccd.client.model.StartEventResponse;

import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CcdApiUpdateCaseTest {

    @Mock CoreCaseDataApi feignCcdApi;
    @Mock CcdAuthenticatorFactory authenticatorFactory;
    @Mock ServiceConfigProvider serviceConfigProvider;
    @Mock Function<StartEventResponse, CaseDataContent> caseDataContentBuilder;

    CcdApi ccdApi;

    @BeforeEach
    void setUp() {
        ccdApi = new CcdApi(feignCcdApi, authenticatorFactory, serviceConfigProvider);
    }

    @Test
    void should_start_and_submit_event_with_correct_data() {
        // given
        var caseDataContent = CaseDataContent.builder().build();
        given(caseDataContentBuilder.apply(any())).willReturn(caseDataContent);

        var jurisdiction = "jurisdiction1";
        var ccdAuthenticator = sampleCcdAuthenticator();
        given(authenticatorFactory.createForJurisdiction(jurisdiction)).willReturn(ccdAuthenticator);

        var startEventResponse = StartEventResponse.builder().build();
        given(feignCcdApi.startEventForCaseWorker(any(), any(), any(), any(), any(), any(), any())).willReturn(
            startEventResponse);

        String caseTypeId = "caseTypeId1";
        String eventId = "eventId1";
        String caseId = "123-123-123";

        // when
        ccdApi.updateCase(jurisdiction, caseTypeId, eventId, caseId, caseDataContentBuilder, "logContext1");

        // then

        verify(feignCcdApi).startEventForCaseWorker(
            ccdAuthenticator.getUserToken(),
            ccdAuthenticator.getServiceToken(),
            ccdAuthenticator.getUserId(),
            jurisdiction,
            caseTypeId,
            caseId,
            eventId
        );

        verify(feignCcdApi).submitEventForCaseWorker(
            ccdAuthenticator.getUserToken(),
            ccdAuthenticator.getServiceToken(),
            ccdAuthenticator.getUserId(),
            jurisdiction,
            caseTypeId,
            caseId,
            true,
            caseDataContent
        );

        verify(caseDataContentBuilder).apply(startEventResponse);
    }

    @Test
    void should_rethrow_exception_when_communication_with_ccd_fails() {
        // given
        var ccdAuthenticator = sampleCcdAuthenticator();
        given(authenticatorFactory.createForJurisdiction(any())).willReturn(ccdAuthenticator);

        var ccdException = mock(FeignException.BadRequest.class);

        willThrow(ccdException)
            .given(feignCcdApi)
            .submitEventForCaseWorker(any(), any(), any(), any(), any(), any(), anyBoolean(), any());

        // when
        Throwable exc = catchThrowable(
            () -> ccdApi.updateCase(
                "jurisdiction",
                "caseTypeId",
                "eventId",
                "caseId",
                caseDataContentBuilder,
                "logContext1"
            )
        );

        // then
        assertThat(exc).isSameAs(ccdException);
    }

    private CcdAuthenticator sampleCcdAuthenticator() {
        return new CcdAuthenticator(
            () -> "serviceToken1",
            "userId1",
            "userToken1"
        );
    }
}
