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
import io.restassured.response.Response
import io.restassured.response.ValidatableResponse
import io.restassured.response.ValidatableResponseOptions
import io.restassured.specification.RequestSpecification
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.hasItem
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.server.LocalServerPort
import org.springframework.test.context.junit.jupiter.SpringExtension
import u.gov.hmcts.reform.bulkscan.orchestrator.controllers.config.PortWaiter.waitFor
import uk.gov.hmcts.reform.bulkscan.orchestrator.controllers.config.Environment.CASE_REF
import uk.gov.hmcts.reform.bulkscan.orchestrator.controllers.config.Environment.CASE_TYPE_BULK_SCAN
import uk.gov.hmcts.reform.bulkscan.orchestrator.controllers.config.Environment.JURIDICTION
import uk.gov.hmcts.reform.bulkscan.orchestrator.controllers.config.IntegrationTest
import uk.gov.hmcts.reform.ccd.client.model.CallbackRequest
import uk.gov.hmcts.reform.ccd.client.model.CallbackRequest.CallbackRequestBuilder
import uk.gov.hmcts.reform.ccd.client.model.CallbackTypes
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails
import uk.gov.hmcts.reform.ccd.client.model.StartEventResponse

typealias ResponseValidation = ValidatableResponseOptions<ValidatableResponse, Response>

fun RequestSpecification.postToCallback(type: String = "attach_case") = post("/callback/{type}", type)
fun RequestSpecification.setBody(builder: CallbackRequestBuilder) = body(builder.build())
fun ResponseValidation.shouldContainError(error: String) = body("errors", hasItem(error))

@ExtendWith(SpringExtension::class)
@IntegrationTest
class AttachExceptionRecordToExistingCaseTest {
    companion object {
        private val mapper: ObjectMapper = ObjectMapper()
        fun asJson(obj: Any): String = mapper.writeValueAsString(obj)
    }

    @LocalServerPort
    private var applicationPort: Int = 0

    @Value("\${wiremock.port}")
    private var wireMockPort: Int = 0
    private val wireMock by lazy { WireMock(wireMockPort) }
    private val startEvent = get(
        "/caseworkers/640/jurisdictions/BULKSCAN/case-types/Bulk_Scanned" +
            "/cases/1539007368674134/event-triggers/attachRecord/token"
    )

    @BeforeEach
    fun before() {
        waitFor(applicationPort)
        wireMock.register(startEvent.willReturn(okJson(asJson(StartEventResponse.builder().build()))))
        RestAssured.requestSpecification = RequestSpecBuilder().setPort(applicationPort).setContentType(JSON).build()
    }

    private val request = CallbackRequest
        .builder()
        .caseDetails(defaultCase().build())
        .eventId(CallbackTypes.ABOUT_TO_SUBMIT)

    private fun defaultCase(): CaseDetails.CaseDetailsBuilder {
        return CaseDetails.builder()
            .jurisdiction(JURIDICTION)
            .caseTypeId(CASE_TYPE_BULK_SCAN)
            .data(mutableMapOf(("attachToCaseReference" to CASE_REF)) as Map<String, Any>?)
    }

    @Test
    fun `should successfully callback with correct information`() {
        given()
            .setBody(request)
            .postToCallback()
            .then()
            .statusCode(200)
            .body("errors.size()", equalTo(0))
    }

    @Test
    fun `should fail with the correct error when no case details is supplied`() {
        given()
            .setBody(request.caseDetails(null))
            .postToCallback()
            .then()
            .statusCode(200)
            .shouldContainError("Internal Error: no case details supplied")
    }

    @Test
    fun `should fail with the correct error when start event api call fails`() {
        wireMock.register(startEvent.willReturn(status(404)))

        given()
            .setBody(request)
            .postToCallback()
            .then()
            .statusCode(200)
            .shouldContainError("Internal Error: start event call failed with 404")
    }

    @Test
    fun `should fail with the correct error when null case data is supplied`() {
        given()
            .setBody(request.caseDetails(defaultCase().data(null).build()))
            .postToCallback()
            .then()
            .statusCode(200)
            .shouldContainError("Internal Error: no case reference found: null")
    }

    @Test
    fun `should fail with the correct error when no case reference supplied`() {
        given()
            .setBody(request.caseDetails(defaultCase().data(mutableMapOf()).build()))
            .postToCallback()
            .then()
            .statusCode(200)
            .shouldContainError("Internal Error: no case reference found: null")
    }

    @Test
    fun `should fail if invalid eventId set`() {
        given()
            .setBody(request.eventId("invalid"))
            .postToCallback()
            .then()
            .statusCode(200)
            .shouldContainError("Internal Error: event-id: invalid invalid")
    }

    @Test
    fun `should fail if no eventId set`() {
        given()
            .setBody(request.eventId(null))
            .postToCallback()
            .then()
            .statusCode(200)
            .shouldContainError("Internal Error: event-id: null invalid")
    }

    @Test
    fun `should create error if type in incorrect`() {
        given()
            .setBody(request)
            .postToCallback("someType")
            .then()
            .statusCode(200)
            .shouldContainError("Internal Error: invalid type supplied: someType")
    }
}
