package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import feign.FeignException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.config.ServiceConfigProvider;
import uk.gov.hmcts.reform.ccd.client.CoreCaseDataApi;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.ccd.client.model.StartEventResponse;
import uk.gov.hmcts.reform.idam.client.models.UserDetails;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
class CcdApiTest {
    @Mock
    private CoreCaseDataApi feignCcdApi;

    @Mock
    private CcdAuthenticatorFactory authenticatorFactory;

    @Mock
    private ServiceConfigProvider serviceConfigProvider;

    @Mock
    private CcdAuthenticator ccdAuthenticator;

    @Mock
    private UserDetails userDetails;

    @Mock
    private CaseDetails caseDetails;

    @Mock
    private StartEventResponse startEventResponse;

    private CcdApi ccdApi;

    @BeforeEach
    public void setUp() {
        ccdApi = new CcdApi(feignCcdApi, authenticatorFactory, serviceConfigProvider);
    }

    @Test
    void should_rethrow_feign_exception_in_startAttachScannedDocs() {
        // given
        given(caseDetails.getJurisdiction()).willReturn("BULKSCAN");
        given(caseDetails.getCaseTypeId()).willReturn("caseTypeId");
        given(authenticatorFactory.createForJurisdiction(anyString())).willReturn(ccdAuthenticator);
        given(ccdAuthenticator.getServiceToken()).willReturn("serviceToken");
        doThrow(
            new FeignException.BadRequest("Msg", "Body".getBytes()))
            .when(feignCcdApi).startEventForCaseWorker(
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString()
            );

        // when
        CcdCallException ccdCallException = catchThrowableOfType(() ->
            ccdApi.startAttachScannedDocs(
                caseDetails,
                "idamToken",
                "userId"
            ), CcdCallException.class
        );

        // then
        assertThat(ccdCallException.getCause().getMessage()).isEqualTo("Msg");
    }

    @Test
    void should_rethrow_feign_exception_in_getCase() {
        // given
        given(authenticatorFactory.createForJurisdiction(anyString())).willReturn(ccdAuthenticator);
        given(ccdAuthenticator.getUserToken()).willReturn("userToken");
        given(ccdAuthenticator.getServiceToken()).willReturn("serviceToken");
        doThrow(new FeignException.BadRequest("Msg", "Body".getBytes()))
            .when(feignCcdApi).getCase(
            anyString(),
            anyString(),
            anyString()
            );

        // when
        InvalidCaseIdException invalidCaseIdException = catchThrowableOfType(() ->
            ccdApi.getCase(
                "caseRef",
                "BULKSCAN"
            ), InvalidCaseIdException.class
        );

        // then
        assertThat(invalidCaseIdException.getCause().getMessage()).isEqualTo("Msg");
    }

    @Test
    void should_rethrow_feign_exception_in_startEvent() {
        // given
        given(ccdAuthenticator.getUserToken()).willReturn("userToken");
        given(ccdAuthenticator.getServiceToken()).willReturn("serviceToken");
        given(ccdAuthenticator.getUserDetails()).willReturn(userDetails);
        given(userDetails.getId()).willReturn("userId");
        doThrow(new FeignException.BadRequest("Msg", "Body".getBytes()))
            .when(feignCcdApi).startEventForCaseWorker(
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString()
            );

        // when
        FeignException feignException = catchThrowableOfType(() ->
            ccdApi.startEvent(
                ccdAuthenticator,
                "jurisdiction",
                "caseTypeId",
                "caseRef",
                "eventTypeId"
            ), FeignException.class
        );

        // then
        assertThat(feignException.getMessage()).isEqualTo("Msg");
    }

    @Test
    void should_rethrow_feign_exception_in_startEvent_withCaseRef_null() {
        // given
        given(ccdAuthenticator.getUserToken()).willReturn("userToken");
        given(ccdAuthenticator.getServiceToken()).willReturn("serviceToken");
        given(ccdAuthenticator.getUserDetails()).willReturn(userDetails);
        given(userDetails.getId()).willReturn("userId");
        doThrow(new FeignException.BadRequest("Msg", "Body".getBytes()))
            .when(feignCcdApi).startForCaseworker(
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString()
            );

        // when
        FeignException feignException = catchThrowableOfType(() ->
            ccdApi.startEvent(
                ccdAuthenticator,
                "jurisdiction",
                "caseTypeId",
                null,
                "eventTypeId"
            ), FeignException.class
        );

        // then
        assertThat(feignException.getMessage()).isEqualTo("Msg");
    }
}
