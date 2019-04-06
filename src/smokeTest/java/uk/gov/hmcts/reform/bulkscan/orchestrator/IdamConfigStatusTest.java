package uk.gov.hmcts.reform.bulkscan.orchestrator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.typesafe.config.ConfigFactory;
import io.restassured.RestAssured;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import uk.gov.hmcts.reform.logging.appinsights.SyntheticHeaders;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource("classpath:application.conf")
class IdamConfigStatusTest {

    private static final String TEST_URL = ConfigFactory.load().getString("test-url");

    @DisplayName("Each configured jurisdiction should have valid credentials")
    @Test
    void each_jurisdiction_should_have_valid_credentials() throws IOException {
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
            String jurisdiction = responseStatus.get("jurisdiction").asText();
            boolean isCorrect = responseStatus.get("is_correct").asBoolean();
            String errorDescription = responseStatus.get("error_description").asText();

            assertThat(isCorrect)
                .withFailMessage(
                    "Misconfigured %s jurisdiction, error description: %s. Check the logs for more details",
                    jurisdiction,
                    errorDescription
                )
                .isTrue();
        });
    }
}
