package uk.gov.hmcts.reform.bulkscan.orchestrator.client.caseupdate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.web.client.RestTemplate;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.caseupdate.model.request.CaseUpdateDetails;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.caseupdate.model.request.CaseUpdateRequest;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.caseupdate.model.request.ExceptionRecord;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.caseupdate.model.request.ExistingCaseDetails;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.caseupdate.model.response.SuccessfulUpdateResponse;

import javax.validation.Validator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;


@ExtendWith(MockitoExtension.class)
class CaseUpdateDataClientTest {

    @Mock
    Validator validator;

    @Mock
    RestTemplate restTemplate;

    private CaseUpdateDataClient caseUpdateDataClient;

    @BeforeEach
    void setUp() {
        caseUpdateDataClient = new CaseUpdateDataClient(validator, restTemplate);
    }

    @Test
    void updateCase_should_use_provided_parameter_to_send_a_http_request() {
        // given
        var request = new CaseUpdateRequest(
            mock(ExceptionRecord.class),
            true,
            mock(CaseUpdateDetails.class),
            mock(ExistingCaseDetails.class)
        );

        String url = "http://test-url.example.com";

        // when
        caseUpdateDataClient.getCaseUpdateData(url, "token", request);

        // then
        var requestCaptor = ArgumentCaptor.forClass(HttpEntity.class);

        verify(restTemplate).postForObject(
            eq(url),
            requestCaptor.capture(),
            eq(SuccessfulUpdateResponse.class)
        );

        assertThat(requestCaptor.getValue().getBody()).isSameAs(request);
    }
}
