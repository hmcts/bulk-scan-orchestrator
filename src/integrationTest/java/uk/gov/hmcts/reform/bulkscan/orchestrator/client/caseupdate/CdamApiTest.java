package uk.gov.hmcts.reform.bulkscan.orchestrator.client.caseupdate;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.cdam.CdamApi;
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.IntegrationTest;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("checkstyle:LineLength")
@AutoConfigureWireMock(port = 0)
@IntegrationTest
public class CdamApiTest {

    @Autowired
    private CdamApi cdamApi;

    @Test
    public void should_return_case_details_for_successful_update() throws Exception {
        // given
        String s2sToken = randomUUID().toString();
        String idamToken = randomUUID().toString();
        String documentUuid = randomUUID().toString();

        stubFor(
            get(urlPathMatching("/cases/documents/" + documentUuid + "/token"))
                .withHeader("ServiceAuthorization", equalTo(s2sToken))
                .withHeader("Authorization", equalTo(idamToken))
                .willReturn(okJson(validHashTokeResponse().toString())));


        String hashToken = cdamApi.getDocumentHash(s2sToken, idamToken, documentUuid);
        assertThat(hashToken).isEqualTo("5dbedb79c7793a21f1cb7402e6b8d1659b2cfdfa4b80418e336914644abde1fb");
    }

    private JSONObject validHashTokeResponse() throws Exception {
        return new JSONObject()
            .put("hashToken", "5dbedb79c7793a21f1cb7402e6b8d1659b2cfdfa4b80418e336914644abde1fb");
    }

}
