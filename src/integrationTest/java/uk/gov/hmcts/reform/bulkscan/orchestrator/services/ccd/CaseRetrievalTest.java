package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.IntegrationTest;
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.ServiceConfigItem;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.config.ServiceConfigProvider;
import uk.gov.hmcts.reform.ccd.client.CoreCaseDataApi;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.givenThat;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.willReturn;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.fileContentAsString;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.config.Environment.CASE_REF;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.config.Environment.CASE_SEARCH_URL;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.config.Environment.EXCEPTION_RECORD_SEARCH_URL;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.config.Environment.GET_CASE_URL;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.config.Environment.JURISDICTION;

@IntegrationTest
@Disabled
class CaseRetrievalTest {

    private static final String TEST_SERVICE_NAME = "bulkscan";

    @Autowired
    private CcdAuthenticatorFactory authenticatorFactory;

    @Autowired
    private CoreCaseDataApi coreCaseDataApi;

    @SpyBean
    private ServiceConfigProvider serviceConfigProvider;

    private CcdApi ccdApi;

    @BeforeEach
    public void setUp() {
        ccdApi = new CcdApi(coreCaseDataApi, authenticatorFactory, serviceConfigProvider);
    }

    @Test
    public void getCase_should_call_ccd_to_retrieve_the_case_by_ccd_id() {
        // given
        givenThat(get(GET_CASE_URL).willReturn(aResponse().withBody(
            fileContentAsString("ccd/response/sample-case.json")
        )));

        // when
        ccdApi.getCase(CASE_REF, JURISDICTION);

        // then
        WireMock.verify(getRequestedFor(urlEqualTo(GET_CASE_URL)));
    }

    @Test
    public void getCaseRefsByLegacyId_should_call_ccd_to_retrieve_ccd_ids_by_legacy_id() {
        // given
        givenThat(post(CASE_SEARCH_URL).willReturn(aResponse().withBody(
            fileContentAsString("ccd/response/search-by-legacy-id/result-empty.json")
        )));

        // when
        ccdApi.getCaseRefsByLegacyId("legacy-id-123", TEST_SERVICE_NAME);

        // then
        WireMock.verify(postRequestedFor(urlEqualTo(CASE_SEARCH_URL)));
    }

    @Test
    public void getCaseRefsByLegacyId_should_not_call_ccd_when_no_case_type_configured() {
        // given
        ServiceConfigItem serviceConfig = new ServiceConfigItem();
        serviceConfig.setCaseTypeIds(emptyList());
        serviceConfig.setService(TEST_SERVICE_NAME);
        serviceConfig.setJurisdiction(JURISDICTION);

        willReturn(serviceConfig).given(serviceConfigProvider).getConfig(TEST_SERVICE_NAME);

        // when
        ccdApi.getCaseRefsByLegacyId("legacy-id-123", TEST_SERVICE_NAME);

        // then
        WireMock.verify(0, postRequestedFor(urlEqualTo(CASE_SEARCH_URL)));
    }

    @Test
    public void getExceptionRecordRefsByEnvelopeId_should_return_empty_list_when_no_records_found() {
        // given
        givenThat(post(EXCEPTION_RECORD_SEARCH_URL).willReturn(aResponse().withBody(
            fileContentAsString("ccd/response/search-by-envelope-id/result-empty.json")
        )));

        // when
        List<Long> ids = ccdApi.getExceptionRecordRefsByEnvelopeId("envelope-id-123", TEST_SERVICE_NAME);

        // then
        assertThat(ids).isEmpty();
        WireMock.verify(postRequestedFor(urlEqualTo(EXCEPTION_RECORD_SEARCH_URL)));
    }

    @Test
    public void getExceptionRecordRefsByEnvelopeId_should_return_list_with_ids_of_all_records_found() {
        // given
        Long foundId1 = 12_345L;
        Long foundId2 = 56_789L;

        String searchResultBody = format(
            fileContentAsString("ccd/response/search-by-envelope-id/result-format-two-records.json"),
            foundId1,
            foundId2
        );

        givenThat(post(EXCEPTION_RECORD_SEARCH_URL).willReturn(aResponse().withBody(searchResultBody)));

        // when
        List<Long> ids = ccdApi.getExceptionRecordRefsByEnvelopeId("envelope-id-123", TEST_SERVICE_NAME);

        // then
        assertThat(ids).containsExactly(foundId1, foundId2);
        WireMock.verify(postRequestedFor(urlEqualTo(EXCEPTION_RECORD_SEARCH_URL)));
    }
}
