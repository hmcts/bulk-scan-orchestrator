package uk.gov.hmcts.reform.bulkscan.orchestrator.controllers

import io.restassured.RestAssured
import io.restassured.RestAssured.given
import io.restassured.builder.RequestSpecBuilder
import io.restassured.http.ContentType.JSON
import io.restassured.response.Response
import io.restassured.response.ValidatableResponse
import io.restassured.response.ValidatableResponseOptions
import io.restassured.specification.RequestSpecification
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.web.server.LocalServerPort
import org.springframework.test.context.junit.jupiter.SpringExtension
import u.gov.hmcts.reform.bulkscan.orchestrator.controllers.config.PortWaiter.waitFor
import uk.gov.hmcts.reform.bulkscan.orchestrator.controllers.config.IntegrationTest
import uk.gov.hmcts.reform.ccd.client.model.CallbackRequest
import uk.gov.hmcts.reform.ccd.client.model.CallbackRequest.CallbackRequestBuilder
import uk.gov.hmcts.reform.ccd.client.model.CallbackTypes
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails

typealias ResponseValidation = ValidatableResponseOptions<ValidatableResponse, Response>

fun RequestSpecification.postToCallback(type: String = "attach_case") = post("/callback/{type}", type)
fun RequestSpecification.setBody(builder: CallbackRequestBuilder) = body(builder.build())
fun ResponseValidation.shouldContainError(error: String) = body("errors", contains(error))

@ExtendWith(SpringExtension::class)
@IntegrationTest
class AttachExceptionRecordToExistingCaseTest {

    @LocalServerPort
    private var applicationPort: Int = 0

    @BeforeEach
    fun before() {
        waitFor(applicationPort)
        RestAssured.requestSpecification = RequestSpecBuilder().setPort(applicationPort).setContentType(JSON).build()
    }

    private val request = CallbackRequest
        .builder()
        .caseDetails(CaseDetails.builder().build())
        .eventId(CallbackTypes.ABOUT_TO_SUBMIT)

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
    fun `should fail with the correct error when no case data is supplied`() {
        given()
            .setBody(request.caseDetails(null))
            .postToCallback()
            .then()
            .statusCode(200)
            .shouldContainError("Internal Error: no Case details supplied")
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
