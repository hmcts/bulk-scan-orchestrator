package uk.gov.hmcts.reform.bulkscan.orchestrator.controllers

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.client.MappingBuilder
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.containing
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
import org.apache.http.HttpHeaders.AUTHORIZATION
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.hasItem
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.server.LocalServerPort
import org.springframework.test.context.junit.jupiter.SpringExtension
import u.gov.hmcts.reform.bulkscan.orchestrator.controllers.config.PortWaiter.waitFor
import uk.gov.hmcts.reform.bulkscan.orchestrator.controllers.config.Environment
import uk.gov.hmcts.reform.bulkscan.orchestrator.controllers.config.Environment.CASE_REF
import uk.gov.hmcts.reform.bulkscan.orchestrator.controllers.config.Environment.JURIDICTION
import uk.gov.hmcts.reform.bulkscan.orchestrator.controllers.config.IntegrationTest
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CallbackValidations
import uk.gov.hmcts.reform.ccd.client.model.CallbackRequest
import uk.gov.hmcts.reform.ccd.client.model.CallbackRequest.CallbackRequestBuilder
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails

typealias ResponseValidation = ValidatableResponseOptions<ValidatableResponse, Response>

fun RequestSpecification.postToCallback(type: String = "attach_case") = post("/callback/{type}", type)
fun RequestSpecification.setBody(builder: CallbackRequestBuilder) = body(builder.build())
fun ResponseValidation.shouldContainError(error: String) = body("errors", hasItem(error))

fun MappingBuilder.authorised() = with(this) {
    withHeader(AUTHORIZATION, containing("eyJhbGciOiJIUzI1NiJ9."))
    //TODO cant seem to make this match
//    withHeader("ServiceAuthorization", containing("eyJhbGciOiJIUzI1NiJ9."))
}

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

    private val getCase = get("/cases/$CASE_REF").authorised()

    private val caseData: CaseDetails = CaseDetails.builder()
        .jurisdiction(Environment.JURIDICTION)
        .caseTypeId(Environment.CASE_TYPE_BULK_SCAN)
        .id(Environment.CASE_REF.toLong())
        .build()

    @BeforeEach
    fun before() {
        waitFor(applicationPort)
        wireMock.register(getCase.willReturn(okJson(asJson(caseData))))
        RestAssured.requestSpecification = RequestSpecBuilder().setPort(applicationPort).setContentType(JSON).build()
    }

    private val callbackRequest = CallbackRequest
        .builder()
        .caseDetails(defaultExceptionCase().build())
        .eventId(CallbackValidations.ATTACH_TO_EXISTING_CASE)

    private fun defaultExceptionCase(): CaseDetails.CaseDetailsBuilder {
        return CaseDetails.builder()
            .jurisdiction(JURIDICTION)
            .caseTypeId("ExceptionRecord")
            .data(mapOf("attachToCaseReference" to CASE_REF))
    }

    @Test
    fun `should successfully callback with correct information`() {
        given()
            .setBody(callbackRequest)
            .postToCallback()
            .then()
            .statusCode(200)
            .body("errors.size()", equalTo(0))
    }

    @Test
    fun `should fail correctly if the case does not exist`() {
        wireMock.register(getCase.willReturn(status(404)))
        given()
            .setBody(callbackRequest)
            .postToCallback()
            .then()
            .statusCode(200)
            .shouldContainError("Could not find case: $CASE_REF")
    }

    @Test
    fun `should fail correctly if ccd is down`() {
        wireMock.register(getCase.willReturn(status(500)))
        given()
            .setBody(callbackRequest)
            .postToCallback()
            .then()
            .statusCode(200)
            .shouldContainError("Internal Error: Could not retrieve case: 1539007368674134 Error: 500")
    }


    @Test
    fun `should fail with the correct error when no case details is supplied`() {
        given()
            .setBody(callbackRequest.caseDetails(null))
            .postToCallback()
            .then()
            .statusCode(200)
            .shouldContainError("Internal Error: no case details supplied")
    }

    @Test
    fun `should fail with the correct error when null case data is supplied`() {
        given()
            .setBody(callbackRequest.caseDetails(defaultExceptionCase().data(null).build()))
            .postToCallback()
            .then()
            .statusCode(200)
            .shouldContainError("Internal Error: no case reference found: null")
    }

    @Test
    fun `should fail with the correct error when no case reference supplied`() {
        given()
            .setBody(callbackRequest.caseDetails(defaultExceptionCase().data(mutableMapOf()).build()))
            .postToCallback()
            .then()
            .statusCode(200)
            .shouldContainError("Internal Error: no case reference found: null")
    }

    @Test
    fun `should fail if invalid eventId set`() {
        given()
            .setBody(callbackRequest.eventId("invalid"))
            .postToCallback()
            .then()
            .statusCode(200)
            .shouldContainError("Internal Error: event-id: invalid invalid")
    }

    @Test
    fun `should fail if no eventId set`() {
        given()
            .setBody(callbackRequest.eventId(null))
            .postToCallback()
            .then()
            .statusCode(200)
            .shouldContainError("Internal Error: event-id: null invalid")
    }

    @Test
    fun `should create error if type in incorrect`() {
        given()
            .setBody(callbackRequest)
            .postToCallback("someType")
            .then()
            .statusCode(200)
            .shouldContainError("Internal Error: invalid type supplied: someType")
    }
}
