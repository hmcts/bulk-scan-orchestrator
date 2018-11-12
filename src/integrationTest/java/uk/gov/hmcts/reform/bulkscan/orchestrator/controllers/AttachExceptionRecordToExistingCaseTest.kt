package uk.gov.hmcts.reform.bulkscan.orchestrator.controllers

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.client.MappingBuilder
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.containing
import com.github.tomakehurst.wiremock.client.WireMock.exactly
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
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

fun document(filename: String, documentNumber: String): Map<String, String> {
    return mapOf(
        "fileName" to filename,
        "id" to documentNumber,
        "someString" to "someValue"
    )
}

fun WiremockReq.withEventSummaryOf(summary: String) =
    withRequestBody(matchingJsonPath("\$.event.summary", WireMock.equalTo(summary)))

const val eventId = "someID"
const val eventToken = "theToken"
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

    private val startEventUrl = "$caseUrl/event-triggers/attachScannedDocs/token"
    private val ccdStartEvent = get(startEventUrl)
        .withAuthorisationHeader()
        .withS2SHeader()

    private val submitUrl = "$caseUrl/events?ignore-warning=true"
    private val ccdSubmitEvent = post(submitUrl)
        .withAuthorisationHeader()
        .withS2SHeader()

    private val filename = "document.pdf"
    private val documentNumber = "123456"
    private val scannedDocument = document(filename, documentNumber)
    private val caseData = mapOf("scannedDocuments" to listOf(scannedDocument))
    private val caseDetails: CaseDetails = CaseDetails.builder()
        .jurisdiction(JURIDICTION)
        .caseTypeId(CASE_TYPE_BULK_SCAN)
        .id(CASE_REF.toLong())
        .data(caseData)
        .build()

    private fun ccdGetCaseMapping() = get("/cases/$CASE_REF").withAuthorisationHeader().withS2SHeader()

    private val recordId = 9876L
    private val exceptionRecordFileName = "record.pdf"
    private val exceptionRecordDocumentNumber = "654321"
    private val scannedRecord = document(exceptionRecordFileName, exceptionRecordDocumentNumber)
    private val exceptionData = exceptionDataWithDoc(scannedRecord)
    private val exceptionRecord = CaseDetails.builder()
        .jurisdiction(JURIDICTION)
        .id(recordId)
        .caseTypeId("ExceptionRecord")
        .data(exceptionData)

    private fun exceptionDataWithDoc(map: Map<String, String>) =
        mapOf("attachToCaseReference" to CASE_REF, "scanRecords" to listOf(map))

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
        wireMock.resetRequests()
        RestAssured.requestSpecification = RequestSpecBuilder().setPort(applicationPort).setContentType(JSON).build()
    }

    private fun submittedScannedRecords() = postRequestedFor(urlEqualTo(submitUrl))
    private fun startEventRequest() = getRequestedFor(urlEqualTo(startEventUrl))

    private val exceptionRecordCallbackBody = CallbackRequest
        .builder()
        .caseDetails(exceptionRecord.build())
        .eventId("attachToExistingCase")

    fun RequestSpecification.setBody(builder: CallbackRequestBuilder = exceptionRecordCallbackBody) =
        body(builder.build())

    @Test
    fun `should successfully callback with correct information`() {
        given()
            .setBody()
            .postToCallback()
            .then()
            .statusCode(200)
            .body("errors.size()", equalTo(0))

        val summary = "Attaching exception record($recordId) document numbers:[$exceptionRecordDocumentNumber] to case:$CASE_REF"

        verify(startEventRequest())
        verify(submittedScannedRecords().numberOfScannedDocumentsIs(2))
        verify(submittedScannedRecords().scannedRecordFilenameAtIndex(0, WireMock.equalTo(filename)))
        verify(submittedScannedRecords().scannedRecordFilenameAtIndex(1, WireMock.equalTo(exceptionRecordFileName)))
        verify(submittedScannedRecords().withEventSummaryOf(summary))
        verify(submittedScannedRecords().withCorrectEventId())
        verify(submittedScannedRecords().withCorrectEventToken())
    }

    @Test
    fun `should fail with the correct error when submit api call fails`() {
        wireMock.register(ccdSubmitEvent.willReturn(status(500)))
        given()
            .setBody()
            .postToCallback()
            .then()
            .statusCode(200)
            .shouldContainError("Internal Error: submitting attach file event failed case: $CASE_REF Error: 500")
    }

    @Test
    fun `should fail with the correct error when start event api call fails`() {
        wireMock.register(ccdStartEvent.willReturn(status(404)))
        given()
            .setBody()
            .postToCallback()
            .then()
            .statusCode(200)
            .shouldContainError("Internal Error: start event call failed case: $CASE_REF Error: 404")
    }

    @Test
    fun `Should fail correctly if document is duplicate or document is already attached`() {
        given().setBody(
            exceptionRecordCallbackBody
                .caseDetails(exceptionRecord.data(exceptionDataWithDoc(scannedDocument)).build())
        )
            .postToCallback()
            .then()
            .statusCode(200)
            .shouldContainError("Document with documentIds [$documentNumber] is already attached to $CASE_REF")

        verify(exactly(0), startEventRequest())
        verify(exactly(0), submittedScannedRecords())
    }

    @Test
    fun `should fail correctly if the case does not exist`() {
        wireMock.register(ccdGetCaseMapping().willReturn(status(404)))
        given()
            .setBody()
            .postToCallback()
            .then()
            .statusCode(200)
            .shouldContainError("Could not find case: $CASE_REF")
    }

    @Test
    fun `should fail correctly if ccd is down`() {
        wireMock.register(ccdGetCaseMapping().willReturn(status(500)))
        given()
            .setBody()
            .postToCallback()
            .then()
            .statusCode(200)
            .shouldContainError("Internal Error: Could not retrieve case: $CASE_REF Error: 500")
    }

    @Test
    fun `should fail with the correct error when no case details is supplied`() {
        given()
            .setBody(exceptionRecordCallbackBody.caseDetails(null))
            .postToCallback()
            .then()
            .statusCode(200)
            .shouldContainError("Internal Error: no case details supplied")
    }

    @Test
    fun `should fail if invalid eventId set`() {
        given()
            .setBody(exceptionRecordCallbackBody.eventId("invalid"))
            .postToCallback()
            .then()
            .statusCode(200)
            .shouldContainError("Internal Error: event-id: invalid invalid")
    }

    @Test
    fun `should fail if no eventId set`() {
        given()
            .setBody(exceptionRecordCallbackBody.eventId(null))
            .postToCallback()
            .then()
            .statusCode(200)
            .shouldContainError("Internal Error: event-id: null invalid")
    }

    @Test
    fun `should create error if type in incorrect`() {
        given()
            .setBody()
            .postToCallback("someType")
            .then()
            .statusCode(200)
            .shouldContainError("Internal Error: invalid type supplied: someType")
    }
}
