package uk.gov.hmcts.reform.bulkscan.orchestrator.controllers

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.okJson
import com.github.tomakehurst.wiremock.client.WireMock.status
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
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.server.LocalServerPort
import org.springframework.test.context.junit.jupiter.SpringExtension
import uk.gov.hmcts.reform.bulkscan.orchestrator.controllers.config.Environment.CASE_TYPE
import uk.gov.hmcts.reform.bulkscan.orchestrator.controllers.config.Environment.JURIDICTION
import uk.gov.hmcts.reform.bulkscan.orchestrator.controllers.config.IntegrationTest
import uk.gov.hmcts.reform.bulkscan.orchestrator.controllers.config.PortWaiter.waitFor
import uk.gov.hmcts.reform.ccd.client.model.CallbackRequest
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails
import uk.gov.hmcts.reform.ccd.client.model.StartEventResponse

@ExtendWith(SpringExtension::class)
@IntegrationTest
class CcdAttachSupplementaryEvidenceTest {

    companion object {
        private val mapper: ObjectMapper = ObjectMapper()
        fun asJson(obj: Any): String = mapper.writeValueAsString(obj)
    }

    @LocalServerPort
    private var applicationPort: Int = 0

    fun RequestSpecification.postToCallback(type: String = "about-to-submit") = post("/callback/{type}", type)
    fun RequestSpecification.setCallbackFields(init: CallbackRequest.CallbackRequestBuilder.() -> CallbackRequest.CallbackRequestBuilder)
        : RequestSpecification {
        val request = CallbackRequest.builder().eventId("attachToExistingCase")
        body(request.init().build())
        return this
    }

    @Value("\${wiremock.port}")
    private var wireMockPort: Int = 0
    private val wireMock by lazy { WireMock(wireMockPort) }

    private val startEvent = get("/caseworkers/640/jurisdictions/BULKSCAN/case-types" +
        "/Bulk_Scanned/cases/someCase/event-triggers/TBD/token")

    private fun baseCase() = CaseDetails.builder().caseTypeId(CASE_TYPE).jurisdiction(JURIDICTION)
    private fun nullCaseData() = baseCase().data(null).build()
    private fun successfulCase() = baseCase().data(mapOf("attachToCaseReference" to "someCase")).build()

    @BeforeEach
    fun before() {
        waitFor(applicationPort)
        RestAssured.requestSpecification = RequestSpecBuilder().setPort(applicationPort).setContentType(JSON).build()
    }

    @Test
    fun `should successfully able to call the ccd event endpoint`() {
        wireMock.register(startEvent.willReturn(okJson(asJson(StartEventResponse.builder().build()))))
        given()
            .setCallbackFields { caseDetails(successfulCase()) }
            .postToCallback()
            .then()
            .statusCode(200)
            .log().everything()
            .body("errors.size()", equalTo(0))
    }

    @Test
    fun `Should return error if 404 returned`() {
        wireMock.register(startEvent.willReturn(status(404)))
        given()
            .setCallbackFields { caseDetails(successfulCase()) }
            .postToCallback()
            .then()
            .statusCode(200)
            .body("errors", contains("Internal Error: response 404 submitting event"))
    }

    @Test
    fun `should return error when fails when case data is null`() {
        given()
            .setCallbackFields { caseDetails(nullCaseData()) }
            .postToCallback()
            .then()
            .statusCode(200)
            .body("errors", contains("Internal Error: No case details supplied eventId: null"))
    }

    @Test
    fun `should return error if case details is null`() {
        given()
            .setCallbackFields { eventId("attachToExistingCase") }
            .postToCallback()
            .then()
            .statusCode(200)
            .body("errors", contains("Internal Error: No case details supplied eventId: attachToExistingCase"))
    }

    @Test
    fun `invalid event type should create error`() {
        given()
            .setCallbackFields { eventId("another-id") }
            .postToCallback("about-to-submit")
            .then()
            .statusCode(200)
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
