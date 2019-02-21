package uk.gov.hmcts.reform.bulkscan.orchestrator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.typesafe.config.ConfigFactory;
import io.restassured.RestAssured;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.out.JurisdictionConfigurationStatus;
import uk.gov.hmcts.reform.logging.appinsights.SyntheticHeaders;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource("classpath:application.conf")
public class IdamConfigStatusTest {

    private static final String TEST_URL = ConfigFactory.load().getString("test-url");

    @DisplayName("Each configured jurisdiction should have valid credentials")
    @Test
    public void each_jurisdiction_should_have_valid_credentials() throws IOException {
        byte[] response = RestAssured
            .given()
            .relaxedHTTPSValidation()
            .baseUri(TEST_URL)
            .header(SyntheticHeaders.SYNTHETIC_TEST_SOURCE, "Bulk Scan Orchestrator smoke test")
            .get("/idam-config-status")
            .andReturn()
            .asByteArray();
        ObjectMapper mapper = new ObjectMapper();

        mapper.readTree(response).elements().forEachRemaining(responseStatus -> {
            JurisdictionConfigurationStatus status = new JurisdictionConfigurationStatus(
                responseStatus.get("jurisdiction").asText(),
                responseStatus.get("is_correct").asBoolean(),
                responseStatus.get("error_description").asText()
            );

            assertThat(status.isCorrect || status.jurisdiction.toUpperCase().equals("BULKSCAN"))
                .withFailMessage(
                    "Misconfigured %s jurisdiction, error description: %s. Check the logs for more details",
                    status.jurisdiction,
                    status.errorDescription
                )
                .isTrue();
            }
        );
    }
}
