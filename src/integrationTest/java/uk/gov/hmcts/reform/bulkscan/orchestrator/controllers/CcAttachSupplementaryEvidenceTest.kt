package uk.gov.hmcts.reform.bulkscan.orchestrator.controllers

import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.boot.web.server.LocalServerPort
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit.jupiter.SpringExtension
import uk.gov.hmcts.reform.bulkscan.orchestrator.controllers.PortWaiter.waitFor
import uk.gov.hmcts.reform.ccd.client.model.CallbackRequest

@ExtendWith(SpringExtension::class)
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("integration")
@AutoConfigureWireMock
@ContextConfiguration(initializers = [IntegrationTestConfig::class])
class CcAttachSupplementaryEvidenceTest {

    @LocalServerPort
    private var applicationPort: Int = 0

    @BeforeEach
    fun before() = waitFor(applicationPort)


    @Test
    fun `should be able to call the ccd event enpoint`() {
        given().baseUri("http://localhost:${applicationPort}")
            .contentType(ContentType.JSON)
            .body(CallbackRequest.builder().build())
            .post("/callback/{type}", "someType")
            .then()
            .statusCode(200)
    }
}
