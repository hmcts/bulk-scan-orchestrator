package uk.gov.hmcts.reform.bulkscan.orchestrator.controllers

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.client.MappingBuilder
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.containing
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.okJson
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.status
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.verify
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import com.github.tomakehurst.wiremock.matching.StringValuePattern
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
import uk.gov.hmcts.reform.bulkscan.orchestrator.controllers.config.Environment.CASE_REF
import uk.gov.hmcts.reform.bulkscan.orchestrator.controllers.config.Environment.CASE_TYPE_BULK_SCAN
import uk.gov.hmcts.reform.bulkscan.orchestrator.controllers.config.Environment.JURIDICTION
import uk.gov.hmcts.reform.bulkscan.orchestrator.controllers.config.Environment.USER_ID
import uk.gov.hmcts.reform.bulkscan.orchestrator.controllers.config.IntegrationTest
import uk.gov.hmcts.reform.ccd.client.model.CallbackRequest
import uk.gov.hmcts.reform.ccd.client.model.CallbackRequest.CallbackRequestBuilder
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails
import uk.gov.hmcts.reform.ccd.client.model.StartEventResponse

typealias ResponseValidation = ValidatableResponseOptions<ValidatableResponse, Response>
typealias WiremockReq = RequestPatternBuilder

fun RequestSpecification.postToCallback(type: String = "attach_case") = post("/callback/{type}", type)
fun RequestSpecification.setBody(builder: CallbackRequestBuilder) = body(builder.build())
fun ResponseValidation.shouldContainError(error: String) = body("errors", hasItem(error))

// see WireMock mapping json files
const val mockedIdamTokenSig = "q6hDG0Z1Qbinwtl8TgeDrAVV0LlCTRtbQqBYoMjd03k"
const val mockedS2sTokenSig = "X1-LdZAd5YgGFP16-dQrpqEICqRmcu1zL_zeCLyUqMjb5DVx7xoU-r8yXHfgd4tmmjGqbsBz_kLqgu8yruSbtg"
fun MappingBuilder.withAuthorisationHeader() = withHeader(AUTHORIZATION, containing(mockedIdamTokenSig))
fun MappingBuilder.withS2SHeader() = withHeader("ServiceAuthorization", containing(mockedS2sTokenSig))

fun WiremockReq.scannedRecordFilenameAtIndex(index: Int, stringValuePattern: StringValuePattern) =
    withRequestBody(matchingJsonPath("\$.data.scannedDocuments[$index].fileName", stringValuePattern))

fun WiremockReq.numberOfScannedDocumentsIs(numberOfDocuments: Int): RequestPatternBuilder =
    withRequestBody(
        matchingJsonPath(
            "\$.data.scannedDocuments.length()",
            WireMock.equalTo(numberOfDocuments.toString())
        )
    )

fun WiremockReq.withEventSummaryOf(summary: String) =
    withRequestBody(matchingJsonPath("\$.event.summary", WireMock.equalTo(summary)))

val eventId = "someID"
val eventToken = "theToken"
fun RequestPatternBuilder.withCorrectEventId() =
    withRequestBody(matchingJsonPath("\$.event.id", WireMock.equalTo(eventId)))
fun RequestPatternBuilder.withCorrectEventToken() =
    withRequestBody(matchingJsonPath("\$.event_token", WireMock.equalTo(eventToken)))

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
    private val caseUrl = "/caseworkers/$USER_ID/jurisdictions/$JURIDICTION" +
        "/case-types/$CASE_TYPE_BULK_SCAN/cases/$CASE_REF"

    private val ccdStartEvent = get(
        "/caseworkers/$USER_ID/jurisdictions/$JURIDICTION/case-types/$CASE_TYPE_BULK_SCAN"
            + "/cases/$CASE_REF/event-triggers/attachScannedDocs/token"
    )
        .withAuthorisationHeader()
        .withS2SHeader()

    private val submitUrl = "$caseUrl/events?ignore-warning=true"
    private val ccdSubmitEvent = post(submitUrl)
        .withAuthorisationHeader()
        .withS2SHeader()

    private val filename2 = "record.pdf"
    private val filename1 = "document.pdf"
    private val scannedDocument = mapOf(
        "fileName" to filename1,
        "documentNumber" to "1234",
        "someString" to "someValue"
    )
    val docNumber = "4321"
    private val scannedRecord = mapOf(
        "fileName" to filename2,
        "documentNumber" to docNumber,
        "someString" to "someValue"
    )
    private val exceptionData = mapOf("attachToCaseReference" to CASE_REF, "scanRecords" to listOf(scannedRecord))
    private val caseData = mapOf("scannedDocuments" to listOf(scannedDocument))

    private val caseDetails: CaseDetails = CaseDetails.builder()
        .jurisdiction(JURIDICTION)
        .caseTypeId(CASE_TYPE_BULK_SCAN)
        .id(CASE_REF.toLong())
        .data(caseData)
        .build()

    private fun ccdGetCaseMapping() = get("/cases/$CASE_REF").withAuthorisationHeader().withS2SHeader()

    val recordId = 9876L
    private val exceptionRecord = CaseDetails.builder()
        .jurisdiction(JURIDICTION)
        .id(recordId)
        .caseTypeId("ExceptionRecord")
        .data(exceptionData)

    private val startEventResponse = StartEventResponse
        .builder()
        .eventId(eventId)
        .token(eventToken).build()

    @BeforeEach
    fun before() {
        waitFor(applicationPort)
        wireMock.register(ccdStartEvent.willReturn(okJson(asJson(startEventResponse))))
        wireMock.register(ccdGetCaseMapping().willReturn(okJson(asJson(caseDetails))))
        wireMock.register(ccdSubmitEvent.willReturn(okJson(asJson(caseDetails))))
        RestAssured.requestSpecification = RequestSpecBuilder().setPort(applicationPort).setContentType(JSON).build()
    }

    private fun submittedScannedRecords() = postRequestedFor(urlEqualTo(submitUrl))

    private val callbackRequest = CallbackRequest
        .builder()
        .caseDetails(exceptionRecord.build())
        .eventId("attachToExistingCase")

    @Test
    fun `should successfully callback with correct information`() {
        given()
            .setBody(callbackRequest)
            .postToCallback()
            .then()
            .statusCode(200)
            .body("errors.size()", equalTo(0))
        verify(submittedScannedRecords().numberOfScannedDocumentsIs(2))
        verify(submittedScannedRecords().scannedRecordFilenameAtIndex(0, WireMock.equalTo(filename1)))
        verify(submittedScannedRecords().scannedRecordFilenameAtIndex(1, WireMock.equalTo(filename2)))
        verify(submittedScannedRecords().withEventSummaryOf("Attaching exception record($recordId) document number:$docNumber to case:$CASE_REF"))
        verify(submittedScannedRecords().withCorrectEventId())
        verify(submittedScannedRecords().withCorrectEventToken())
    }

    @Test
    fun `should fail with the correct error when submit api call fails`() {
        wireMock.register(ccdSubmitEvent.willReturn(status(500)))
        given()
            .setBody(callbackRequest)
            .postToCallback()
            .then()
            .statusCode(200)
            .shouldContainError("Internal Error: submitting attach file event failed case: $CASE_REF Error: 500")
    }

    @Test
    fun `should fail with the correct error when start event api call fails`() {
        wireMock.register(ccdStartEvent.willReturn(status(404)))
        given()
            .setBody(callbackRequest)
            .postToCallback()
            .then()
            .statusCode(200)
            .shouldContainError("Internal Error: start event call failed case: $CASE_REF Error: 404")
    }

    @Test
    fun `should fail correctly if the case does not exist`() {
        wireMock.register(ccdGetCaseMapping().willReturn(status(404)))
        given()
            .setBody(callbackRequest)
            .postToCallback()
            .then()
            .statusCode(200)
            .shouldContainError("Could not find case: $CASE_REF")
    }

    @Test
    fun `should fail correctly if ccd is down`() {
        wireMock.register(ccdGetCaseMapping().willReturn(status(500)))
        given()
            .setBody(callbackRequest)
            .postToCallback()
            .then()
            .statusCode(200)
            .shouldContainError("Internal Error: Could not retrieve case: $CASE_REF Error: 500")
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
