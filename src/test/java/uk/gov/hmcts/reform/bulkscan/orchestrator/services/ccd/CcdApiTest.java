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
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CcdApiTest {
    @Mock
    CoreCaseDataApi feignCcdApi;
    @Mock
    CcdAuthenticatorFactory authenticatorFactory;
    @Mock
    ServiceConfigProvider serviceConfigProvider;
    @Mock
    Function<StartEventResponse, CaseDataContent> caseDataContentBuilder;

    private CcdApi ccdApi;

    @BeforeEach
    void setUp() {
        ccdApi = new CcdApi(feignCcdApi, authenticatorFactory, serviceConfigProvider);
    }

    @Test
    void start_event_should_start_event_with_correct_data() {
        // given
        var userToken = "userToken";
        var serviceToken = "serviceToken";
        var userId = "userId";
        var jurisdiction = "jurisdiction1";
        var caseTypeId = "caseTypeId1";
        var eventId = "eventId1";
        var caseId = "123-123-123";

        var startEventResponse = StartEventResponse.builder().build();
        given(feignCcdApi.startEventForCaseWorker(
            userToken,
            serviceToken,
            userId,
            jurisdiction,
            caseTypeId,
            caseId,
            eventId
        ))
            .willReturn(startEventResponse);

        // when
        ccdApi.startEventForCaseWorker(
            userToken,
            serviceToken,
            userId,
            jurisdiction,
            caseTypeId,
            caseId,
            eventId
        );

        // then
        verify(feignCcdApi).startEventForCaseWorker(
            userToken,
            serviceToken,
            userId,
            jurisdiction,
            caseTypeId,
            caseId,
            eventId
        );
    }

    @Test
    void start_event_should_rethrow_exception_when_communication_with_ccd_fails() {
        // given
        var userToken = "userToken";
        var serviceToken = "serviceToken";
        var userId = "userId";
        var jurisdiction = "jurisdiction1";
        var caseTypeId = "caseTypeId1";
        var eventId = "eventId1";
        var caseId = "123-123-123";

        var ccdException = mock(FeignException.BadRequest.class);

        given(feignCcdApi.startEventForCaseWorker(
            userToken,
            serviceToken,
            userId,
            jurisdiction,
            caseTypeId,
            caseId,
            eventId
        ))
            .willThrow(ccdException);

        // when
        Throwable exc = catchThrowable(
            () -> ccdApi.startEventForCaseWorker(
                userToken,
                serviceToken,
                userId,
                jurisdiction,
                caseTypeId,
                caseId,
                eventId
            )
        );

        // then
        assertThat(exc).isSameAs(ccdException);
    }
}
