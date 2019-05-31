package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import com.github.tomakehurst.wiremock.matching.StringValuePattern;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import org.assertj.core.util.Maps;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.server.LocalServerPort;
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.IntegrationTest;
import uk.gov.hmcts.reform.ccd.client.model.CallbackRequest;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.ccd.client.model.StartEventResponse;

import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.givenThat;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.status;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.apache.http.HttpHeaders.AUTHORIZATION;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.config.Environment.CASE_REF;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.config.Environment.CASE_SUBMIT_URL;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.config.Environment.CASE_TYPE_BULK_SCAN;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.config.Environment.CASE_TYPE_EXCEPTION_RECORD;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.config.Environment.JURISDICTION;

@IntegrationTest
class AttachExceptionRecordToExistingCaseTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @LocalServerPort
    private int applicationPort;

    private static final String CASE_URL = CASE_SUBMIT_URL + "/" + CASE_REF;

    private static final String START_EVENT_URL = CASE_URL + "/event-triggers/attachScannedDocs/token";
    // see WireMock mapping json files
    private static final String MOCKED_IDAM_TOKEN_SIG = "q6hDG0Z1Qbinwtl8TgeDrAVV0LlCTRtbQqBYoMjd03k";
    private static final String MOCKED_S2S_TOKEN_SIG =
        "X1-LdZAd5YgGFP16-dQrpqEICqRmcu1zL_zeCLyUqMjb5DVx7xoU-r8yXHfgd4tmmjGqbsBz_kLqgu8yruSbtg";

    private MappingBuilder ccdStartEvent() {
        return get(START_EVENT_URL)
            .withHeader(AUTHORIZATION, containing(MOCKED_IDAM_TOKEN_SIG))
            .withHeader("ServiceAuthorization", containing(MOCKED_S2S_TOKEN_SIG));
    }

    private static final String SUBMIT_URL = CASE_URL + "/events?ignore-warning=true";

    private MappingBuilder ccdSubmitEvent() {
        return post(SUBMIT_URL)
            .withHeader(AUTHORIZATION, containing(MOCKED_IDAM_TOKEN_SIG))
            .withHeader("ServiceAuthorization", containing(MOCKED_S2S_TOKEN_SIG));
    }

    private static final String EVENT_ID = "someID";
    private static final String EVENT_TOKEN = "theToken";

    private static final String DOCUMENT_FILENAME = "document.pdf";
    private static final String DOCUMENT_NUMBER = "123456";
    private static final Map<String, Object> SCANNED_DOCUMENT = document(DOCUMENT_FILENAME, DOCUMENT_NUMBER);
    private static final Map<String, Object> CASE_DATA = ImmutableMap.of(
        "scannedDocuments", ImmutableList.of(SCANNED_DOCUMENT)
    );

    private static final CaseDetails CASE_DETAILS = CaseDetails.builder()
        .jurisdiction(JURISDICTION)
        .caseTypeId(CASE_TYPE_BULK_SCAN)
        .id(Long.parseLong(CASE_REF))
        .data(CASE_DATA)
        .build();

    private MappingBuilder ccdGetCaseMapping() {
        return get("/cases/" + CASE_REF)
            .withHeader(AUTHORIZATION, containing(MOCKED_IDAM_TOKEN_SIG))
            .withHeader("ServiceAuthorization", containing(MOCKED_S2S_TOKEN_SIG));
    }

    private static final long EXCEPTION_RECORD_ID = 26409983479785245L;
    private static final String exceptionRecordFileName = "record.pdf";
    private static final String exceptionRecordDocumentNumber = "654321";
    private static final Map<String, Object> scannedRecord = document(
        exceptionRecordFileName,
        exceptionRecordDocumentNumber
    );

    private CaseDetails.CaseDetailsBuilder exceptionRecordBuilder() {
        return CaseDetails.builder()
            .jurisdiction(JURISDICTION)
            .id(EXCEPTION_RECORD_ID)
            .caseTypeId("ExceptionRecord")
            .data(exceptionDataWithDoc(scannedRecord, CASE_REF));
    }

    private static Map<String, Object> document(String filename, String documentNumber) {
        return ImmutableMap.of(
            "fileName", filename,
            "id", UUID.randomUUID().toString(),
            "value", ImmutableMap.of(
                "controlNumber", documentNumber,
                "someNumber", 3
            ),
            "someString", "someValue"
        );
    }

    private StartEventResponse startEventResponse = StartEventResponse
        .builder()
        .eventId(EVENT_ID)
        .token(EVENT_TOKEN)
        .build();

    @BeforeEach
    void before() throws JsonProcessingException {
        givenThat(ccdStartEvent().willReturn(okJson(MAPPER.writeValueAsString(startEventResponse))));
        givenThat(ccdGetCaseMapping().willReturn(okJson(MAPPER.writeValueAsString(CASE_DETAILS))));
        givenThat(ccdSubmitEvent().willReturn(okJson(MAPPER.writeValueAsString(CASE_DETAILS))));

        givenThat(
            ccdGetExceptionRecord()
                .willReturn(
                    // an exception record not attached to any case
                    okJson(
                        MAPPER.writeValueAsString(exceptionRecordDetails(null))
                    )
                )
        );

        RestAssured.requestSpecification = new RequestSpecBuilder()
            .setPort(applicationPort)
            .setContentType(JSON)
            .build();
    }


    private RequestPatternBuilder submittedScannedRecords() {
        return postRequestedFor(urlEqualTo(SUBMIT_URL));
    }

    private RequestPatternBuilder startEventRequest() {
        return getRequestedFor(urlEqualTo(START_EVENT_URL));
    }

    private CallbackRequest.CallbackRequestBuilder exceptionRecordCallbackBodyBuilder() {
        return CallbackRequest
            .builder()
            .caseDetails(exceptionRecordBuilder().build())
            .eventId("attachToExistingCase");
    }

    @DisplayName("Should successfully callback with correct information")
    @Test
    void should_successfully_callback_with_correct_information() {
        given()
            .body(exceptionRecordCallbackBodyBuilder().build())
            .post("/callback/{type}", "attach_case")
            .then()
            .statusCode(200)
            .body("errors.size()", equalTo(0));

        verify(startEventRequest());
        verifyRequestPattern(
            submittedScannedRecords(),
            "$.data.scannedDocuments.length()",
            WireMock.equalTo("2")
        );
        verifyRequestPattern(
            submittedScannedRecords(),
            "$.data.scannedDocuments[0].fileName",
            WireMock.equalTo(DOCUMENT_FILENAME)
        );
        verifyRequestPattern(
            submittedScannedRecords(),
            "$.data.scannedDocuments[1].fileName",
            WireMock.equalTo(exceptionRecordFileName)
        );
        verifyRequestPattern(
            submittedScannedRecords(),
            "$.event.summary",
            WireMock.equalTo(String.format(
                "Attaching exception record(%d) document numbers:[%s] to case:%s",
                EXCEPTION_RECORD_ID,
                exceptionRecordDocumentNumber,
                CASE_REF
            ))
        );
        verifyRequestPattern(
            submittedScannedRecords(),
            "$.event.id",
            WireMock.equalTo(EVENT_ID)
        );
        verifyRequestPattern(
            submittedScannedRecords(),
            "$.event_token",
            WireMock.equalTo(EVENT_TOKEN)
        );
    }

    @DisplayName("Should fail with the correct error when submit api call fails")
    @Test
    void should_fail_with_the_correct_error_when_submit_api_call_fails() {
        givenThat(ccdSubmitEvent().willReturn(status(500)));

        given()
            .body(exceptionRecordCallbackBodyBuilder().build())
            .post("/callback/{type}", "attach_case")
            .then()
            .statusCode(200)
            .body("errors", hasItem(AttachCaseCallbackService.INTERNAL_ERROR_MSG));
    }

    @DisplayName("Should fail with the correct error when start event api call fails")
    @Test
    void should_fail_with_the_correct_error_when_start_event_api_call_fails() {
        givenThat(ccdStartEvent().willReturn(status(404)));

        given()
            .body(exceptionRecordCallbackBodyBuilder().build())
            .post("/callback/{type}", "attach_case")
            .then()
            .statusCode(200)
            .body("errors", hasItem(AttachCaseCallbackService.INTERNAL_ERROR_MSG));
    }

    @DisplayName("Should fail correctly if document is duplicate or document is already attached")
    @Test
    void should_fail_correctly_if_document_is_duplicate_or_document_is_already_attached() {
        given()
            .body(attachToCaseRequest(CASE_REF))
            .post("/callback/{type}", "attach_case")
            .then()
            .statusCode(200)
            .body("errors", hasItem(String.format(
                "Document(s) with control number [%s] are already attached to case reference: %s",
                DOCUMENT_NUMBER,
                CASE_REF
            )));

        verify(exactly(0), startEventRequest());
        verify(exactly(0), submittedScannedRecords());
    }

    @DisplayName("Should fail correctly if the case does not exist")
    @Test
    void should_fail_correctly_if_the_case_does_not_exist() {
        givenThat(ccdGetCaseMapping().willReturn(status(404)));

        given()
            .body(exceptionRecordCallbackBodyBuilder().build())
            .post("/callback/{type}", "attach_case")
            .then()
            .statusCode(200)
            .body("errors", hasItem("Could not find case: " + CASE_REF));
    }

    @Test
    void should_fail_correctly_if_the_case_id_is_invalid() {
        givenThat(ccdGetCaseMapping().willReturn(status(400)));

        given()
            .body(exceptionRecordCallbackBodyBuilder().build())
            .post("/callback/{type}", "attach_case")
            .then()
            .statusCode(200)
            .body("errors", hasItem("Invalid case ID: " + CASE_REF));
    }

    @DisplayName("Should fail correctly if ccd is down")
    @Test
    void should_fail_correctly_if_ccd_is_down() {
        givenThat(ccdGetCaseMapping().willReturn(status(500)));

        given()
            .body(exceptionRecordCallbackBodyBuilder().build())
            .post("/callback/{type}", "attach_case")
            .then()
            .statusCode(200)
            .body("errors", hasItem(AttachCaseCallbackService.INTERNAL_ERROR_MSG));
    }

    @DisplayName("Should fail with the correct error when no case details is supplied")
    @Test
    void should_fail_with_the_correct_error_when_no_case_details_is_supplied() {
        given()
            .body(exceptionRecordCallbackBodyBuilder().caseDetails(null).build())
            .post("/callback/{type}", "attach_case")
            .then()
            .statusCode(200)
            .body("errors", hasItem("Internal Error: callback or case details were empty"));
    }

    @Test
    void should_fail_when_exception_record_is_already_attached_to_a_case() throws Exception {
        String caseRef = "1234567890123456";
        givenThat(
            ccdGetExceptionRecord()
                .willReturn(
                    // return an exception record already attached to some case
                    okJson(
                        MAPPER.writeValueAsString(
                            exceptionRecordDetails(caseRef)
                        )
                    )
                )
        );

        given()
            .body(attachToCaseRequest(CASE_REF))
            .post("/callback/{type}", "attach_case")
            .then()
            .statusCode(200)
            .body("errors", hasItem("Exception record is already attached to case " + caseRef));
    }

    @DisplayName("Should create error if type in incorrect")
    @Test
    void should_create_error_if_type_in_incorrect() {
        given()
            .body(exceptionRecordCallbackBodyBuilder().build())
            .post("/callback/{type}", "someType")
            .then()
            .statusCode(404);
    }

    private CallbackRequest attachToCaseRequest(String attachToCaseReference) {
        return exceptionRecordCallbackBodyBuilder()
            .caseDetails(
                exceptionRecordBuilder()
                    .data(exceptionDataWithDoc(SCANNED_DOCUMENT, attachToCaseReference))
                    .build()
            )
            .build();
    }

    private void verifyRequestPattern(RequestPatternBuilder builder, String jsonPath, StringValuePattern pattern) {
        verify(builder.withRequestBody(matchingJsonPath(jsonPath, pattern)));
    }

    private CaseDetails exceptionRecordDetails(String attachToCaseReference) {
        return CaseDetails.builder()
            .jurisdiction(JURISDICTION)
            .caseTypeId(CASE_TYPE_EXCEPTION_RECORD)
            .id(EXCEPTION_RECORD_ID)
            .data(exceptionDataWithDoc(scannedRecord, attachToCaseReference))
            .build();
    }

    private Map<String, Object> exceptionDataWithDoc(
        Map<String, Object> scannedDocument,
        String attachToCaseReference
    ) {
        Map<String, Object> exceptionData = Maps.newHashMap("scannedDocuments", ImmutableList.of(scannedDocument));

        if (attachToCaseReference != null) {
            exceptionData.put("attachToCaseReference", attachToCaseReference);
        }

        return exceptionData;
    }

    private MappingBuilder ccdGetExceptionRecord() {
        return get("/cases/" + EXCEPTION_RECORD_ID)
            .withHeader(AUTHORIZATION, containing(MOCKED_IDAM_TOKEN_SIG))
            .withHeader("ServiceAuthorization", containing(MOCKED_S2S_TOKEN_SIG));
    }
}
