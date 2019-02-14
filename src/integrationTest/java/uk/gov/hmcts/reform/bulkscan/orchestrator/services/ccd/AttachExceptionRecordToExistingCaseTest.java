package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import com.github.tomakehurst.wiremock.matching.StringValuePattern;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.context.annotation.Lazy;
import org.springframework.test.context.junit.jupiter.SpringExtension;
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
import static uk.gov.hmcts.reform.bulkscan.orchestrator.config.Environment.JURISDICTION;

@ExtendWith(SpringExtension.class)
@IntegrationTest
class AttachExceptionRecordToExistingCaseTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @LocalServerPort
    private int applicationPort;

    @Lazy
    @Autowired
    private WireMockServer server;

    private static final String caseUrl = CASE_SUBMIT_URL + "/" + CASE_REF;

    private static final String startEventUrl = caseUrl + "/event-triggers/attachScannedDocs/token";
    // see WireMock mapping json files
    private static final String mockedIdamTokenSig = "q6hDG0Z1Qbinwtl8TgeDrAVV0LlCTRtbQqBYoMjd03k";
    private static final String mockedS2sTokenSig =
        "X1-LdZAd5YgGFP16-dQrpqEICqRmcu1zL_zeCLyUqMjb5DVx7xoU-r8yXHfgd4tmmjGqbsBz_kLqgu8yruSbtg";

    private MappingBuilder ccdStartEvent() {
        return get(startEventUrl)
            .withHeader(AUTHORIZATION, containing(mockedIdamTokenSig))
            .withHeader("ServiceAuthorization", containing(mockedS2sTokenSig));
    }

    private static final String submitUrl = caseUrl + "/events?ignore-warning=true";

    private MappingBuilder ccdSubmitEvent() {
        return post(submitUrl)
            .withHeader(AUTHORIZATION, containing(mockedIdamTokenSig))
            .withHeader("ServiceAuthorization", containing(mockedS2sTokenSig));
    }

    private static final String eventId = "someID";
    private static final String eventToken = "theToken";

    private static final String filename = "document.pdf";
    private static final String documentNumber = "123456";
    private static final Map<String, Object> scannedDocument = document(filename, documentNumber);
    private static final Map<String, Object> caseData = ImmutableMap.of(
        "scannedDocuments", ImmutableList.of(scannedDocument)
    );
    private static final CaseDetails caseDetails = CaseDetails.builder()
        .jurisdiction(JURISDICTION)
        .caseTypeId(CASE_TYPE_BULK_SCAN)
        .id(Long.parseLong(CASE_REF))
        .data(caseData)
        .build();

    private MappingBuilder ccdGetCaseMapping() {
        return get("/cases/" + CASE_REF)
            .withHeader(AUTHORIZATION, containing(mockedIdamTokenSig))
            .withHeader("ServiceAuthorization", containing(mockedS2sTokenSig));
    }

    private static final long recordId = 9876L;
    private static final String exceptionRecordFileName = "record.pdf";
    private static final String exceptionRecordDocumentNumber = "654321";
    private static final Map<String, Object> scannedRecord = document(
        exceptionRecordFileName,
        exceptionRecordDocumentNumber
    );
    private static final Map<String, Object> exceptionData = exceptionDataWithDoc(scannedRecord);

    private CaseDetails.CaseDetailsBuilder exceptionRecordBuilder() {
        return CaseDetails.builder()
            .jurisdiction(JURISDICTION)
            .id(recordId)
            .caseTypeId("ExceptionRecord")
            .data(exceptionData);
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

    private static Map<String, Object> exceptionDataWithDoc(Map<String, Object> map) {
        return ImmutableMap.of(
            "attachToCaseReference", CASE_REF,
            "scannedDocuments", ImmutableList.of(map)
        );
    }

    private StartEventResponse startEventResponse = StartEventResponse
        .builder()
        .eventId(eventId)
        .token(eventToken)
        .build();

    @BeforeEach
    void before() throws JsonProcessingException {
        WireMock.configureFor(server.port());

        givenThat(ccdStartEvent().willReturn(okJson(MAPPER.writeValueAsString(startEventResponse))));
        givenThat(ccdGetCaseMapping().willReturn(okJson(MAPPER.writeValueAsString(caseDetails))));
        givenThat(ccdSubmitEvent().willReturn(okJson(MAPPER.writeValueAsString(caseDetails))));

        server.resetRequests();

        RestAssured.requestSpecification = new RequestSpecBuilder()
            .setPort(applicationPort)
            .setContentType(JSON)
            .build();
    }

    private RequestPatternBuilder submittedScannedRecords() {
        return postRequestedFor(urlEqualTo(submitUrl));
    }

    private RequestPatternBuilder startEventRequest() {
        return getRequestedFor(urlEqualTo(startEventUrl));
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
            WireMock.equalTo(filename)
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
                recordId,
                exceptionRecordDocumentNumber,
                CASE_REF
            ))
        );
        verifyRequestPattern(
            submittedScannedRecords(),
            "$.event.id",
            WireMock.equalTo(eventId)
        );
        verifyRequestPattern(
            submittedScannedRecords(),
            "$.event_token",
            WireMock.equalTo(eventToken)
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
            .body("errors", hasItem(String.format(
                "Internal Error: submitting attach file event failed case: %s Error: 500",
                CASE_REF
            )));
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
            .body("errors", hasItem(String.format(
                "Internal Error: start event call failed case: %s Error: 404",
                CASE_REF
            )));
    }

    @DisplayName("Should fail correctly if document is duplicate or document is already attached")
    @Test
    void should_fail_correctly_if_document_is_duplicate_or_document_is_already_attached() {
        given()
            .body(exceptionRecordCallbackBodyBuilder()
                .caseDetails(exceptionRecordBuilder()
                    .data(exceptionDataWithDoc(scannedDocument))
                    .build()
                )
                .build()
            )
            .post("/callback/{type}", "attach_case")
            .then()
            .statusCode(200)
            .body("errors", hasItem(String.format(
                "Document(s) with control number [%s] are already attached to case reference: %s",
                documentNumber,
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
            .body("errors", hasItem(String.format(
                "Case not found. Ref: %s, jurisdiction: %s",
                CASE_REF,
                JURISDICTION
            )));
    }

    @DisplayName("Should fail correctly if the case reference is invalid")
    @Test
    void should_fail_correctly_if_the_case_reference_is_invalid() {
        givenThat(ccdGetCaseMapping().willReturn(status(400)));

        given()
            .body(exceptionRecordCallbackBodyBuilder().build())
            .post("/callback/{type}", "attach_case")
            .then()
            .statusCode(200)
            .body("errors", hasItem(String.format(
                "Invalid Case Ref: %s, jurisdiction: %s",
                CASE_REF,
                JURISDICTION
            )));
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
            .body("errors", hasItem(String.format(
                "Internal Error: Could not retrieve case: %s Error: 500",
                CASE_REF
            )));
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

    @DisplayName("Should create error if type in incorrect")
    @Test
    void should_create_error_if_type_in_incorrect() {
        given()
            .body(exceptionRecordCallbackBodyBuilder().build())
            .post("/callback/{type}", "someType")
            .then()
            .statusCode(404);
    }

    private void verifyRequestPattern(RequestPatternBuilder builder, String jsonPath, StringValuePattern pattern) {
        verify(builder.withRequestBody(matchingJsonPath(jsonPath, pattern)));
    }
}
