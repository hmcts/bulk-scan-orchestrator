package uk.gov.hmcts.reform.bulkscan.orchestrator.controllers

import io.restassured.RestAssured
import io.restassured.RestAssured.given
import io.restassured.builder.RequestSpecBuilder
import io.restassured.http.ContentType.JSON
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.boot.web.server.LocalServerPort
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit.jupiter.SpringExtension
import uk.gov.hmcts.reform.bulkscan.orchestrator.controllers.PortWaiter.waitFor
import uk.gov.hmcts.reform.bulkscan.orchestrator.controllers.config.IntegrationTest
import uk.gov.hmcts.reform.ccd.client.model.CallbackRequest

@ExtendWith(SpringExtension::class)
@IntegrationTest
class CcdAttachSupplementaryEvidenceTest {

    @LocalServerPort
    private var applicationPort: Int = 0

    @BeforeEach
    fun before() {
        waitFor(applicationPort)
        RestAssured.requestSpecification = RequestSpecBuilder().setPort(applicationPort).setContentType(JSON).build()
    }

    @Test
    fun `should be able to call the ccd event endpoint`() {
        given()
            .body(CallbackRequest.builder().build())
            .post("/callback/{type}", "someType")
            .then()
            .statusCode(200)
    }
}
