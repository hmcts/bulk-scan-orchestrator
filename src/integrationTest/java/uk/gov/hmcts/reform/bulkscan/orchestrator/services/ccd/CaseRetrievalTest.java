package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.IntegrationTest;
import uk.gov.hmcts.reform.ccd.client.CoreCaseDataApi;

import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.config.Environment.CASE_REF;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.config.Environment.GET_CASE_URL;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.config.Environment.JURISDICTION;

@ExtendWith(SpringExtension.class)
@IntegrationTest
class CaseRetrievalTest {

    @Autowired
    @Lazy
    private WireMockServer server;

    @Autowired
    @Lazy
    private CcdAuthenticatorFactory factory;

    @Autowired
    @Lazy
    private CoreCaseDataApi coreCaseDataApi;

    @DisplayName("Should call to retrieve the case from ccd")
    @Test
    void should_call_to_retrieve_the_case_from_ccd() {
        // given
        CaseRetriever caseRetriever = new CaseRetriever(factory, coreCaseDataApi);

        // when
        caseRetriever.retrieve(JURISDICTION, CASE_REF);

        // then
        server.verify(getRequestedFor(urlEqualTo(GET_CASE_URL)));
    }
}
