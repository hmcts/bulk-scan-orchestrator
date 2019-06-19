package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.IntegrationTest;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.config.ServiceConfigProvider;
import uk.gov.hmcts.reform.ccd.client.CoreCaseDataApi;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.givenThat;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.fileContentAsString;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.config.Environment.CASE_REF;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.config.Environment.GET_CASE_URL;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.config.Environment.JURISDICTION;

@IntegrationTest
class CaseRetrievalTest {

    @Autowired
    private CcdAuthenticatorFactory authenticatorFactory;

    @Autowired
    private CoreCaseDataApi coreCaseDataApi;

    @Autowired
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
}
