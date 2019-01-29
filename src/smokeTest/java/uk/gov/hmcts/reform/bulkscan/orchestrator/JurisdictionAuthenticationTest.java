package uk.gov.hmcts.reform.bulkscan.orchestrator;

import com.typesafe.config.ConfigFactory;
import io.restassured.RestAssured;
import io.restassured.mapper.TypeRef;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;
import uk.gov.hmcts.reform.logging.appinsights.SyntheticHeaders;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource("classpath:application.conf")
public class JurisdictionAuthenticationTest {

    private static final String TEST_URL = ConfigFactory.load().getString("test-url");

    @DisplayName("Each configured jurisdiction should have valid credentials")
    @Test
    public void each_jurisdiction_should_have_valid_credentials() {
        String failMessage = "Misconfigured %s jurisdiction, response code: %s. Check the logs for more details";
        Map<String, HttpStatus> response = RestAssured
            .given()
            .relaxedHTTPSValidation()
            .baseUri(TEST_URL)
            .header(SyntheticHeaders.SYNTHETIC_TEST_SOURCE, "Bulk Scan Orchestrator smoke test")
            .get("/jurisdictions")
            .as(new TypeRef<Map<String, HttpStatus>>() {});

        response.forEach((jurisdiction, httpStatus) -> assertThat(httpStatus)
            .withFailMessage(failMessage, jurisdiction, httpStatus)
            .isEqualTo(HttpStatus.OK)
        );
    }
}
