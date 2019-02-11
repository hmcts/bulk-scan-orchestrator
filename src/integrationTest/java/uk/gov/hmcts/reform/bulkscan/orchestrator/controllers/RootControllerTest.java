package uk.gov.hmcts.reform.bulkscan.orchestrator.controllers;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.test.context.junit4.SpringRunner;
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.IntegrationTest;

import static io.restassured.RestAssured.given;

@RunWith(SpringRunner.class)
@IntegrationTest
public class RootControllerTest {
    @LocalServerPort
    int serverPort;

    @Test
    public void call_to_root_endpoint_should_result_with_204_response() {
        given().get("http://localhost:" + serverPort + "/")
            .then()
            .statusCode(204);
    }
}
