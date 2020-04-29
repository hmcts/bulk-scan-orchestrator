package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import com.github.tomakehurst.wiremock.client.WireMock;
import feign.FeignException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.willReturn;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.fileContentAsString;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.config.Environment.CASE_REF;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.config.Environment.CASE_SEARCH_URL;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.config.Environment.EXCEPTION_RECORD_SEARCH_URL;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.config.Environment.GET_CASE_URL;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.config.Environment.JURISDICTION;

@AutoConfigureWireMock(port = 0)
@IntegrationTest
class CaseRetrievalTest {

    private static final String TEST_SERVICE_NAME = "bulkscan";

    @SpyBean
    private CcdAuthenticatorFactory authenticatorFactory;

    @Autowired
    private CoreCaseDataApi coreCaseDataApi;

    @SpyBean
    private ServiceConfigProvider serviceConfigProvider;

    private CcdApi ccdApi;

    @BeforeEach
    public void setUp() {
        WireMock.reset();
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
    public void getCase_should_return_CcdCallException_when_403_exception() {
        // given
        givenThat(get(GET_CASE_URL).willReturn(aResponse().withStatus(403)));

        // when
        assertThatThrownBy(() -> ccdApi.getCase(CASE_REF, JURISDICTION))
            .isInstanceOf(CcdCallException.class)
            .hasMessageContaining("Internal Error: Could not retrieve case: 1539007368674134 Error: 403");

        // then
        WireMock.verify(getRequestedFor(urlEqualTo(GET_CASE_URL)));
        Mockito.verify(authenticatorFactory).removeFromCache(JURISDICTION);
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
    public void getCaseRefsByLegacyId_should_return_feignException_when_401_error() {
        // given
        givenThat(post(CASE_SEARCH_URL).willReturn(aResponse().withStatus(401)));

        // when
        assertThatThrownBy(() -> ccdApi.getCaseRefsByLegacyId("legacy-id-123", TEST_SERVICE_NAME))
            .isInstanceOf(FeignException.class)
            .hasMessageContaining("401 Unauthorized");


        // then
        WireMock.verify(postRequestedFor(urlEqualTo(CASE_SEARCH_URL)));
        Mockito.verify(authenticatorFactory).removeFromCache(JURISDICTION);
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
    public void getExceptionRecordRefsByEnvelopeId_should_return_feignException_when_403_errro() {
        // given
        givenThat(post(EXCEPTION_RECORD_SEARCH_URL).willReturn(aResponse().withStatus(403)));

        // when
        assertThatThrownBy(() -> ccdApi.getExceptionRecordRefsByEnvelopeId("envelope-id-123", TEST_SERVICE_NAME))
            .isInstanceOf(FeignException.class)
            .hasMessageContaining("403 Forbidden");
        // then
        WireMock.verify(postRequestedFor(urlEqualTo(EXCEPTION_RECORD_SEARCH_URL)));
        Mockito.verify(authenticatorFactory).removeFromCache(JURISDICTION);
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

    @Test
    public void getCaseByBulkScanCaseReference_should_return_empty_list_when_no_records_found() {
        // given
        givenThat(post(CASE_SEARCH_URL).willReturn(aResponse().withBody(
            fileContentAsString("ccd/response/search-by-bulk-scan-case-reference-id/result-empty.json")
        )));

        // when
        List<Long> ids = ccdApi.getCaseRefsByBulkScanCaseReference("ref-123", TEST_SERVICE_NAME);

        // then
        assertThat(ids).isEmpty();
        WireMock.verify(postRequestedFor(urlEqualTo(CASE_SEARCH_URL)));
    }

    @Test
    public void getCaseByBulkScanCaseReference_should_return_list_with_ids_of_all_records_found() {
        // given
        Long foundId1 = 12_345L;

        String searchResultBody = format(
            fileContentAsString("ccd/response/search-by-bulk-scan-case-reference-id/result-format-single-record.json"),
            foundId1
        );

        givenThat(post(CASE_SEARCH_URL).willReturn(aResponse().withBody(searchResultBody)));

        // when
        List<Long> ids = ccdApi.getCaseRefsByBulkScanCaseReference("ref-123", TEST_SERVICE_NAME);

        // then
        assertThat(ids).containsExactly(foundId1);
        WireMock.verify(postRequestedFor(urlEqualTo(CASE_SEARCH_URL)));
    }
}
