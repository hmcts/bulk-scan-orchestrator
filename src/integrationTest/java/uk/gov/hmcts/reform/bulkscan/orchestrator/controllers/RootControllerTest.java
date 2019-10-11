package uk.gov.hmcts.reform.bulkscan.orchestrator.controllers;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.server.LocalServerPort;
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.IntegrationTest;

import static io.restassured.RestAssured.given;

@IntegrationTest
@Disabled
class RootControllerTest {
    @LocalServerPort
    int serverPort;

    @Test
    void call_to_root_endpoint_should_result_with_204_response() {
        given().get("http://localhost:" + serverPort + "/")
            .then()
            .statusCode(204);
    }
}
