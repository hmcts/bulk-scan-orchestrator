package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import feign.FeignException;
import feign.Response;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.http.HttpStatus;
import uk.gov.hmcts.reform.ccd.client.CoreCaseDataApi;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.util.Collections;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatCode;
import static org.mockito.BDDMockito.given;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.AUTH_DETAILS;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.CASE_ID;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.CASE_REF;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.JURSIDICTION;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.SERVICE_TOKEN;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.THE_CASE;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.USER_TOKEN;

@RunWith(MockitoJUnitRunner.class)
public class CaseRetrieverTest {
    @Mock
    private CoreCaseDataApi dataApi;
    @Mock
    private CcdAuthenticatorFactory authenticator;

    private CaseRetriever retriever;

    @Test
    public void should_retrieve_case_successfully() {
        retriever = new CaseRetriever(authenticator, dataApi);

        given(dataApi.getCase(USER_TOKEN, SERVICE_TOKEN, CASE_REF))
            .willReturn(THE_CASE);
        given(authenticator.createForJurisdiction(JURSIDICTION)).willReturn(AUTH_DETAILS);

        CaseDetails theCase = retriever.retrieve(JURSIDICTION, CASE_REF);
        assertThat(theCase.getId()).isEqualTo(CASE_ID);
    }

    @Test
    public void should_return_null_for_when_the_case_is_not_found() {
        retriever = new CaseRetriever(authenticator, dataApi);
        FeignException exception = FeignException.errorStatus(
            "methodKey",
            Response
                .builder()
                .headers(Collections.emptyMap())
                .status(HttpStatus.NOT_FOUND.value())
                .reason("not found")
                .build()
        );

        given(dataApi.getCase(USER_TOKEN, SERVICE_TOKEN, CASE_REF))
            .willThrow(exception);
        given(authenticator.createForJurisdiction(JURSIDICTION)).willReturn(AUTH_DETAILS);

        CaseDetails theCase = retriever.retrieve(JURSIDICTION, CASE_REF);

        assertThat(theCase).isNull();
    }

    @Test
    public void should_throw_exception_when_api_response_is_other_than_not_found_feign_exception() {
        retriever = new CaseRetriever(authenticator, dataApi);
        FeignException exception = FeignException.errorStatus(
            "methodKey",
            Response
                .builder()
                .headers(Collections.emptyMap())
                .status(HttpStatus.UNAUTHORIZED.value())
                .reason("unauthorised")
                .build()
        );

        given(dataApi.getCase(USER_TOKEN, SERVICE_TOKEN, CASE_REF))
            .willThrow(exception);
        given(authenticator.createForJurisdiction(JURSIDICTION)).willReturn(AUTH_DETAILS);

        assertThatCode(() -> retriever.retrieve(JURSIDICTION, CASE_REF)).isEqualTo(exception);
    }
}
