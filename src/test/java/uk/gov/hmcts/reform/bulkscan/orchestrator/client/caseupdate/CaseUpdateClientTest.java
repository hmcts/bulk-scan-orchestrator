package uk.gov.hmcts.reform.bulkscan.orchestrator.client.caseupdate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.web.client.RestTemplate;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.CaseUpdateRequestCreator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.caseupdate.model.request.CaseUpdateDetails;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.caseupdate.model.request.CaseUpdateRequest;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.caseupdate.model.request.ExistingCaseDetails;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.caseupdate.model.response.SuccessfulUpdateResponse;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.request.ExceptionRecord;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import javax.validation.Validator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.client.SampleData.sampleExceptionRecord;


@ExtendWith(MockitoExtension.class)
class CaseUpdateClientTest {

    @Mock
    Validator validator;

    @Mock
    RestTemplate restTemplate;

    @Mock
    CaseUpdateRequestCreator requestCreator;

    private CaseUpdateClient caseUpdateClient;

    @BeforeEach
    void setUp() {
        caseUpdateClient = new CaseUpdateClient(validator, restTemplate, requestCreator);
    }

    @Test
    void updateCase_use_request_creator_for_making_request_body() {
        // given
        var request = new CaseUpdateRequest(
            mock(ExceptionRecord.class),
            true,
            mock(CaseUpdateDetails.class),
            mock(ExistingCaseDetails.class)
        );

        given(requestCreator.create(any(), any())).willReturn(request);

        var existingCase = CaseDetails.builder().build();
        var exceptionRecord = sampleExceptionRecord();

        String url = "http://test-url.example.com";

        // when
        caseUpdateClient.updateCase(url, existingCase, exceptionRecord, "token");

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
