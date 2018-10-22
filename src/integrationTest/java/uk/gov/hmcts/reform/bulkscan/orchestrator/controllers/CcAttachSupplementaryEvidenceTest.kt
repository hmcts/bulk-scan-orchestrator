package uk.gov.hmcts.reform.bulkscan.orchestrator.controllers

import io.restassured.RestAssured
import io.restassured.RestAssured.given
import io.restassured.builder.RequestSpecBuilder
import io.restassured.http.ContentType.JSON
import io.restassured.specification.RequestSpecification
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.web.server.LocalServerPort
import org.springframework.test.context.junit.jupiter.SpringExtension
import uk.gov.hmcts.reform.bulkscan.orchestrator.controllers.config.IntegrationTest
import uk.gov.hmcts.reform.bulkscan.orchestrator.controllers.config.PortWaiter.waitFor
import uk.gov.hmcts.reform.ccd.client.model.CallbackRequest
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails

@ExtendWith(SpringExtension::class)
@IntegrationTest
class CcdAttachSupplementaryEvidenceTest {

    @LocalServerPort
    private var applicationPort: Int = 0

    fun RequestSpecification.postToCallback(type: String = "about-to-submit") = post("/callback/{type}", type)
    fun RequestSpecification.setCallbackFields(init: CallbackRequest.CallbackRequestBuilder.() -> CallbackRequest.CallbackRequestBuilder)
        : RequestSpecification {
        val request = CallbackRequest.builder()
        body(request.init().build())
        return this
    }

    @BeforeEach
    fun before() {
        waitFor(applicationPort)
        RestAssured.requestSpecification = RequestSpecBuilder().setPort(applicationPort).setContentType(JSON).build()
    }

    @Test
    fun `should successfully able to call the ccd event endpoint`() {
        given()
            .setCallbackFields {
                eventId("attachToExistingCase")
                caseDetails(CaseDetails.builder().build())
            }
            .postToCallback()
            .then()
            .statusCode(200)
            .log().everything()
            .body("errors.size()", equalTo(0))
    }

    @Test
    fun `should return error if case details is null`() {
        given()
            .setCallbackFields {
                eventId("attachToExistingCase")
            }
            .postToCallback()
            .then()
            .statusCode(200)
            .log().everything()
            .body("errors", contains("Internal Error: No case details supplied"))
    }

    @Test
    fun `invalid event type should create error`() {
        given()
            .setCallbackFields { eventId("another-id") }
            .postToCallback("about-to-submit")
            .then()
            .statusCode(200)
            .log().everything()
            .body("errors", contains("Internal Error: Invalid event ID:another-id"))
    }

    @Test
    fun `invalid type should return an error`() {
        given()
            .setCallbackFields { eventId("another-id") }
            .postToCallback("someType")
            .then()
            .statusCode(200)
            .body("errors", contains("Internal Error: invalid event: someType"))
    }
}
