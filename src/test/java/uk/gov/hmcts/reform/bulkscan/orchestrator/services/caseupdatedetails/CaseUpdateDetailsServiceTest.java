package uk.gov.hmcts.reform.bulkscan.orchestrator.services.caseupdatedetails;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGenerator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.caseupdate.CaseUpdateDataClient;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.caseupdate.CaseUpdateRequestCreator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.caseupdate.model.request.CaseUpdateRequest;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.caseupdate.model.response.SuccessfulUpdateResponse;
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.ServiceConfigItem;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.internal.ExceptionRecord;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.config.ServiceConfigProvider;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.client.SampleData.sampleCaseDetails;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.client.SampleData.sampleExceptionRecord;

@ExtendWith(MockitoExtension.class)
class CaseUpdateDetailsServiceTest {

    @Mock AuthTokenGenerator s2sTokenGenerator;
    @Mock CaseUpdateDataClient caseUpdateDataClient;
    @Mock CaseUpdateRequestCreator requestCreator;
    @Mock ServiceConfigProvider serviceConfigProvider;

    @Mock CaseUpdateRequest caseUpdateRequest;
    @Mock SuccessfulUpdateResponse updateDataResponse;

    CaseUpdateDetailsService service;

    @BeforeEach
    void setUp() {
        service = new CaseUpdateDetailsService(
            s2sTokenGenerator,
            caseUpdateDataClient,
            requestCreator,
            serviceConfigProvider
        );
    }

    @Test
    void should_call_api_client_with_correct_arguments() {
        // given
        CaseDetails existingCase = sampleCaseDetails();
        ExceptionRecord exceptionRecord = sampleExceptionRecord();

        given(s2sTokenGenerator.generate()).willReturn("some-s2s-token");
        given(serviceConfigProvider.getConfig("some-service")).willReturn(cfg("some-url"));
        given(requestCreator.create(exceptionRecord, existingCase)).willReturn(caseUpdateRequest);
        given(caseUpdateDataClient.getCaseUpdateData("some-url", "some-s2s-token", caseUpdateRequest))
            .willReturn(updateDataResponse);

        // when
        var result = service.getCaseUpdateData("some-service", existingCase, exceptionRecord);

        // then
        assertThat(result).isEqualTo(updateDataResponse);
    }

    private ServiceConfigItem cfg(String updateUrl) {
        var cfg = new ServiceConfigItem();
        cfg.setUpdateUrl(updateUrl);
        return cfg;
    }
}
