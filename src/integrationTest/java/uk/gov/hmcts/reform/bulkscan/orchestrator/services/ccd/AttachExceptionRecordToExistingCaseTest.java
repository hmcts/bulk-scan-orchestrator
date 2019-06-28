package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import com.github.tomakehurst.wiremock.matching.StringValuePattern;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;
import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.path.json.JsonPath;
import io.restassured.response.ValidatableResponse;
import org.assertj.core.util.Maps;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.server.LocalServerPort;
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.IntegrationTest;
import uk.gov.hmcts.reform.ccd.client.model.CallbackRequest;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.ccd.client.model.StartEventResponse;

import java.io.IOException;
import java.nio.charset.Charset;
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
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.config.Environment.CASE_REF;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.config.Environment.CASE_SUBMIT_URL;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.config.Environment.CASE_TYPE_BULK_SCAN;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.config.Environment.CASE_TYPE_EXCEPTION_RECORD;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.config.Environment.JURISDICTION;

@IntegrationTest
class AttachExceptionRecordToExistingCaseTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    public static final String CASE_REFERENCE_TYPE_EXTERNAL = "externalCaseReference";
    public static final String CASE_REFERENCE_TYPE_CCD = "ccdCaseReference";

    private static final String CASE_URL = CASE_SUBMIT_URL + "/" + CASE_REF;

    private static final String START_EVENT_URL = CASE_URL + "/event-triggers/attachScannedDocs/token";
    // see WireMock mapping json files
    private static final String MOCKED_IDAM_TOKEN_SIG = "q6hDG0Z1Qbinwtl8TgeDrAVV0LlCTRtbQqBYoMjd03k";
    private static final String MOCKED_S2S_TOKEN_SIG =
        "X1-LdZAd5YgGFP16-dQrpqEICqRmcu1zL_zeCLyUqMjb5DVx7xoU-r8yXHfgd4tmmjGqbsBz_kLqgu8yruSbtg";

    private static final String SUBMIT_URL = CASE_URL + "/events?ignore-warning=true";

    private static final String EVENT_ID = "someID";
    private static final String EVENT_TOKEN = "theToken";

    private static final String DOCUMENT_FILENAME = "document.pdf";
    private static final String DOCUMENT_NUMBER = "123456";
    private static final Map<String, Object> EXISTING_DOC = document(DOCUMENT_FILENAME, DOCUMENT_NUMBER);

    private static final Map<String, Object> CASE_DATA = ImmutableMap.of(
        "scannedDocuments", ImmutableList.of(EXISTING_DOC),
        "evidenceHandled", "No"
    );

    private static final CaseDetails CASE_DETAILS = CaseDetails.builder()
        .jurisdiction(JURISDICTION)
        .caseTypeId(CASE_TYPE_BULK_SCAN)
        .id(Long.parseLong(CASE_REF))
        .data(CASE_DATA)
        .build();

    private static final long EXCEPTION_RECORD_ID = 26409983479785245L;
    private static final String EXCEPTION_RECORD_FILENAME = "record.pdf";
    private static final String EXCEPTION_RECORD_DOCUMENT_NUMBER = "654321";

    private static final Map<String, Object> EXCEPTION_RECORD_DOC = document(
        EXCEPTION_RECORD_FILENAME,
        EXCEPTION_RECORD_DOCUMENT_NUMBER
    );

    private static final StartEventResponse START_EVENT_RESPONSE = StartEventResponse
        .builder()
        .eventId(EVENT_ID)
        .token(EVENT_TOKEN)
        .build();

    private static final String SERVICE_AUTHORIZATION_HEADER = "ServiceAuthorization";
    private static final String RESPONSE_FIELD_ERRORS = "errors";
    private static final String RESPONSE_FIELD_WARNINGS = "warnings";
    private static final String RESPONSE_FIELD_DATA = "data";

    @LocalServerPort
    private int applicationPort;

    @BeforeEach
    public void before() throws JsonProcessingException {
        givenThat(ccdStartEvent().willReturn(okJson(MAPPER.writeValueAsString(START_EVENT_RESPONSE))));
        mockCaseSearchByCcdId(CASE_REF, okJson(MAPPER.writeValueAsString(CASE_DETAILS)));
        givenThat(ccdSubmitEvent().willReturn(okJson(MAPPER.writeValueAsString(CASE_DETAILS))));

        mockCaseSearchByCcdId(
            String.valueOf(EXCEPTION_RECORD_ID),
            // an exception record not attached to any case
            okJson(
                MAPPER.writeValueAsString(exceptionRecord(null))
            )
        );

        RestAssured.requestSpecification = new RequestSpecBuilder()
            .setPort(applicationPort)
            .setContentType(JSON)
            .build();
    }

    @DisplayName("Should successfully callback with correct information")
    @Test
    public void should_callback_with_correct_information_when_attaching_by_attachToCaseReference() {
        ValidatableResponse response =
            given()
                .body(exceptionRecordCallbackRequest(CASE_REF))
                .post("/callback/{type}", "attach_case")
                .then()
                .statusCode(200);

        verifySuccessResponseWithAttachToCaseReference(response);
        verifyRequestedAttachingToCase();
    }

    @Test
    public void should_callback_with_correct_information_when_attaching_by_ccd_search_case_reference() {
        ValidatableResponse response =
            given()
                .body(
                    exceptionRecordCallbackRequest(
                        null,
                        CASE_REFERENCE_TYPE_CCD,
                        CASE_REF,
                        CASE_TYPE_EXCEPTION_RECORD
                    )
                )
                .post("/callback/{type}", "attach_case")
                .then()
                .statusCode(200);

        verifySuccessResponseWithAttachToCaseReference(response);
        verifyRequestedAttachingToCase();
    }

    @Test
    public void should_callback_with_correct_information_when_attaching_by_legacy_id() throws Exception {
        String legacyId = "legacy-id-123";

        mockCaseSearchByLegacyId(
            legacyId,
            okJson(
                getSearchResponseContent(
                    "ccd/response/search-by-legacy-id/result-format-single-case.json",
                    CASE_REF
                )
            )
        );

        ValidatableResponse response =
            given()
                .body(
                    exceptionRecordCallbackRequest(
                        null,
                        CASE_REFERENCE_TYPE_EXTERNAL,
                        legacyId,
                        CASE_TYPE_EXCEPTION_RECORD
                    )
                )
                .post("/callback/{type}", "attach_case")
                .then()
                .statusCode(200);

        verifySuccessResponseWithAttachToCaseReference(response);
        verifyRequestedAttachingToCase();
    }

    @DisplayName("Should fail with the correct error when submit api call fails")
    @Test
    public void should_fail_with_the_correct_error_when_submit_api_call_fails() {
        givenThat(ccdSubmitEvent().willReturn(status(500)));

        given()
            .body(exceptionRecordCallbackRequest())
            .post("/callback/{type}", "attach_case")
            .then()
            .statusCode(200)
            .body(RESPONSE_FIELD_ERRORS, hasItem(AttachCaseCallbackService.INTERNAL_ERROR_MSG));
    }

    @DisplayName("Should fail with the correct error when start event api call fails")
    @Test
    public void should_fail_with_the_correct_error_when_start_event_api_call_fails() {
        givenThat(ccdStartEvent().willReturn(status(404)));

        given()
            .body(exceptionRecordCallbackRequest())
            .post("/callback/{type}", "attach_case")
            .then()
            .statusCode(200)
            .body(RESPONSE_FIELD_ERRORS, hasItem(AttachCaseCallbackService.INTERNAL_ERROR_MSG));
    }

    @DisplayName("Should fail correctly if document is duplicate or document is already attached")
    @Test
    public void should_fail_correctly_if_document_is_duplicate_or_document_is_already_attached() {
        given()
            .body(attachToCaseRequest(CASE_REF, null, null, EXISTING_DOC))
            .post("/callback/{type}", "attach_case")
            .then()
            .statusCode(200)
            .body(RESPONSE_FIELD_ERRORS, hasItem(String.format(
                "Document(s) with control number [%s] are already attached to case reference: %s",
                DOCUMENT_NUMBER,
                CASE_REF
            )));

        verify(exactly(0), startEventRequest());
        verify(exactly(0), submittedScannedRecords());
    }

    @DisplayName("Should fail correctly if the case does not exist")
    @Test
    public void should_fail_when_case_referenced_by_attachToCaseReference_does_not_exist() {
        mockCaseSearchByCcdId(CASE_REF, status(404));

        given()
            .body(exceptionRecordCallbackRequest())
            .post("/callback/{type}", "attach_case")
            .then()
            .statusCode(200)
            .body(RESPONSE_FIELD_ERRORS, hasItem("Could not find case: " + CASE_REF));
    }

    @Test
    public void should_fail_when_case_referenced_by_ccd_search_case_reference_does_not_exist() {
        String nonExistingCaseRef = CASE_REF;
        mockCaseSearchByCcdId(nonExistingCaseRef, status(404));

        given()
            .body(
                exceptionRecordCallbackRequest(
                    null,
                    CASE_REFERENCE_TYPE_CCD,
                    nonExistingCaseRef,
                    CASE_TYPE_EXCEPTION_RECORD
                )
            )
            .post("/callback/{type}", "attach_case")
            .then()
            .statusCode(200)
            .body(RESPONSE_FIELD_ERRORS, hasItem("Could not find case: " + nonExistingCaseRef));
    }

    @Test
    public void should_fail_when_there_is_no_case_with_given_legacy_id() throws Exception {
        String nonExistingLegacyId = "non-existing-id-123";
        mockCaseSearchByCcdId(nonExistingLegacyId, status(404));
        mockCaseSearchByLegacyId(
            nonExistingLegacyId,
            okJson(
                getSearchResponseContent("ccd/response/search-by-legacy-id/result-empty.json")
            )
        );

        given()
            .body(
                exceptionRecordCallbackRequest(
                    null,
                    CASE_REFERENCE_TYPE_EXTERNAL,
                    nonExistingLegacyId,
                    CASE_TYPE_EXCEPTION_RECORD
                )
            )
            .post("/callback/{type}", "attach_case")
            .then()
            .statusCode(200)
            .body(RESPONSE_FIELD_ERRORS, hasItem("No case found for legacy case reference " + nonExistingLegacyId));
    }

    @Test
    public void should_fail_when_there_are_multiple_cases_with_given_legacy_id() throws Exception {
        String legacyId = "legacy-id-123";
        String caseOneCcdId = "1539007368600001";
        String caseTwoCcdId = "1539007368600002";

        mockSearchByLegacyIdToReturnTwoCases(legacyId, caseOneCcdId, caseTwoCcdId);

        String expectedErrorMessage = String.format(
            "Multiple cases (%s, %s) found for the given legacy case reference: %s",
            caseOneCcdId,
            caseTwoCcdId,
            legacyId
        );

        given()
            .body(
                exceptionRecordCallbackRequest(
                    null,
                    CASE_REFERENCE_TYPE_EXTERNAL,
                    legacyId,
                    CASE_TYPE_EXCEPTION_RECORD
                )
            )
            .post("/callback/{type}", "attach_case")
            .then()
            .statusCode(200)
            .body(RESPONSE_FIELD_ERRORS, hasItem(expectedErrorMessage));
    }

    @Test
    public void should_fail_correctly_if_the_case_id_is_invalid() {
        mockCaseSearchByCcdId(CASE_REF, status(400));

        given()
            .body(exceptionRecordCallbackRequest())
            .post("/callback/{type}", "attach_case")
            .then()
            .statusCode(200)
            .body(RESPONSE_FIELD_ERRORS, hasItem("Invalid case ID: " + CASE_REF));
    }

    @Test
    public void should_fail_when_exception_record_case_type_id_is_invalid() {
        given()
            .body(
                exceptionRecordCallbackRequest(
                    CASE_REF,
                    null,
                    null,
                    "invalid-case-type"
                )
            )
            .post("/callback/{type}", "attach_case")
            .then()
            .statusCode(200)
            .body(RESPONSE_FIELD_ERRORS, hasItem("Case type ID (invalid-case-type) has invalid format"));
    }

    @Test
    public void should_fail_when_search_case_reference_type_is_invalid() {
        given()
            .body(
                exceptionRecordCallbackRequest(
                    null, "invalid-reference-type",
                    "search-case-reference",
                    CASE_TYPE_EXCEPTION_RECORD
                )
            )
            .post("/callback/{type}", "attach_case")
            .then()
            .statusCode(200)
            .body(RESPONSE_FIELD_ERRORS, hasItem("Invalid case reference type supplied: invalid-reference-type"));
    }

    @Test
    public void should_fail_when_search_case_reference_is_invalid() {
        given()
            .body(
                exceptionRecordCallbackRequest(
                    null,
                    CASE_REFERENCE_TYPE_CCD,
                    "invalid-ccd-reference",
                    CASE_TYPE_EXCEPTION_RECORD
                )
            )
            .post("/callback/{type}", "attach_case")
            .then()
            .statusCode(200)
            .body(RESPONSE_FIELD_ERRORS, hasItem("Invalid case reference: 'invalid-ccd-reference'"));
    }

    @DisplayName("Should fail correctly if ccd is down")
    @Test
    public void should_fail_correctly_if_ccd_is_down() {
        mockCaseSearchByCcdId(CASE_REF, status(500));

        given()
            .body(exceptionRecordCallbackRequest(CASE_REF))
            .post("/callback/{type}", "attach_case")
            .then()
            .statusCode(200)
            .body(RESPONSE_FIELD_ERRORS, hasItem(AttachCaseCallbackService.INTERNAL_ERROR_MSG));
    }

    @DisplayName("Should fail with the correct error when no case details is supplied")
    @Test
    public void should_fail_with_the_correct_error_when_no_case_details_is_supplied() {
        CallbackRequest callbackRequest = exceptionRecordCallbackRequest();
        callbackRequest.setCaseDetails(null);

        given()
            .body(callbackRequest)
            .post("/callback/{type}", "attach_case")
            .then()
            .statusCode(200)
            .body(RESPONSE_FIELD_ERRORS, hasItem("Internal Error: callback or case details were empty"));
    }

    @Test
    public void should_fail_when_exception_record_is_already_attached_to_a_case() throws Exception {
        String caseRef = "1234567890123456";

        mockCaseSearchByCcdId(
            String.valueOf(EXCEPTION_RECORD_ID),
            // return an exception record already attached to some case
            okJson(
                MAPPER.writeValueAsString(exceptionRecord(caseRef))
            )
        );

        given()
            .body(attachToCaseRequest(CASE_REF))
            .post("/callback/{type}", "attach_case")
            .then()
            .statusCode(200)
            .body(RESPONSE_FIELD_ERRORS, hasItem("Exception record is already attached to case " + caseRef));
    }

    @DisplayName("Should create error if type in incorrect")
    @Test
    public void should_create_error_if_type_in_incorrect() {
        given()
            .body(exceptionRecordCallbackRequest())
            .post("/callback/{type}", "someType")
            .then()
            .statusCode(404);
    }

    private CallbackRequest attachToCaseRequest(String attachToCaseReference) {
        return attachToCaseRequest(attachToCaseReference, null, null, EXCEPTION_RECORD_DOC);
    }

    private CallbackRequest attachToCaseRequest(
        String attachToCaseReference,
        String searchCaseReferenceType,
        String searchCaseReference,
        Map<String, Object> document
    ) {
        return exceptionRecordCallbackRequest(
            attachToCaseReference,
            searchCaseReferenceType,
            searchCaseReference,
            CASE_TYPE_EXCEPTION_RECORD,
            document
        );
    }

    private void verifyRequestPattern(RequestPatternBuilder builder, String jsonPath, StringValuePattern pattern) {
        verify(builder.withRequestBody(matchingJsonPath(jsonPath, pattern)));
    }

    private Map<String, Object> exceptionDataWithDoc(
        Map<String, Object> scannedDocument,
        String attachToCaseReference,
        String searchCaseReferenceType,
        String searchCaseReference
    ) {
        Map<String, Object> exceptionData =
            Maps.newHashMap("scannedDocuments", ImmutableList.of(scannedDocument));

        if (attachToCaseReference != null) {
            exceptionData.put("attachToCaseReference", attachToCaseReference);
        }

        if (searchCaseReferenceType != null) {
            exceptionData.put("searchCaseReferenceType", searchCaseReferenceType);
        }

        if (searchCaseReference != null) {
            exceptionData.put("searchCaseReference", searchCaseReference);
        }

        return exceptionData;
    }

    private void verifyRequestedAttachingToCase() {
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
            WireMock.equalTo(EXCEPTION_RECORD_FILENAME)
        );
        verifyRequestPattern(
            submittedScannedRecords(),
            "$.event.summary",
            WireMock.equalTo(String.format(
                "Attaching exception record(%d) document numbers:[%s] to case:%s",
                EXCEPTION_RECORD_ID,
                EXCEPTION_RECORD_DOCUMENT_NUMBER,
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

    private void verifySuccessResponseWithAttachToCaseReference(ValidatableResponse response) {
        JsonPath responseJson = response.extract().jsonPath();

        assertThat(responseJson.getList(RESPONSE_FIELD_ERRORS)).isNullOrEmpty();
        assertThat(responseJson.getList(RESPONSE_FIELD_WARNINGS)).isNullOrEmpty();

        Map<String, Object> responseData = responseJson.getMap(RESPONSE_FIELD_DATA);
        assertThat(responseData).isNotNull();
        assertThat(responseData.get("attachToCaseReference")).isEqualTo(CASE_REF);
        assertThat(responseData.get("evidenceHandled")).isEqualTo("No");
    }

    private CaseDetails exceptionRecord(String attachToCaseReference) {
        return exceptionRecord(
            attachToCaseReference,
            null,
            null,
            CASE_TYPE_EXCEPTION_RECORD,
            EXCEPTION_RECORD_DOC
        );
    }

    private CaseDetails exceptionRecord(
        String attachToCaseReference,
        String searchCaseReferenceType,
        String searchCaseReference,
        String caseTypeId,
        Map<String, Object> document
    ) {
        return CaseDetails.builder()
            .jurisdiction(JURISDICTION)
            .id(EXCEPTION_RECORD_ID)
            .caseTypeId(caseTypeId)
            .data(
                exceptionDataWithDoc(
                    document,
                    attachToCaseReference,
                    searchCaseReferenceType,
                    searchCaseReference
                )
            ).build();
    }

    private void mockCaseSearchByCcdId(String ccdId, ResponseDefinitionBuilder responseBuilder) {
        givenThat(
            get("/cases/" + ccdId)
                .withHeader(AUTHORIZATION, containing(MOCKED_IDAM_TOKEN_SIG))
                .withHeader(SERVICE_AUTHORIZATION_HEADER, containing(MOCKED_S2S_TOKEN_SIG))
                .willReturn(responseBuilder)
        );
    }

    private void mockCaseSearchByLegacyId(
        String legacyId,
        ResponseDefinitionBuilder responseBuilder
    ) {
        givenThat(
            post("/searchCases?ctid=" + CASE_TYPE_BULK_SCAN)
                .withHeader(AUTHORIZATION, containing(MOCKED_IDAM_TOKEN_SIG))
                .withHeader(SERVICE_AUTHORIZATION_HEADER, containing(MOCKED_S2S_TOKEN_SIG))
                .withRequestBody(
                    matchingJsonPath("$.query.match_phrase", containing(legacyId))
                )
                .willReturn(responseBuilder)
        );
    }

    private MappingBuilder ccdSubmitEvent() {
        return post(SUBMIT_URL)
            .withHeader(AUTHORIZATION, containing(MOCKED_IDAM_TOKEN_SIG))
            .withHeader(SERVICE_AUTHORIZATION_HEADER, containing(MOCKED_S2S_TOKEN_SIG));
    }

    private MappingBuilder ccdStartEvent() {
        return get(START_EVENT_URL)
            .withHeader(AUTHORIZATION, containing(MOCKED_IDAM_TOKEN_SIG))
            .withHeader(SERVICE_AUTHORIZATION_HEADER, containing(MOCKED_S2S_TOKEN_SIG));
    }

    private RequestPatternBuilder submittedScannedRecords() {
        return postRequestedFor(urlEqualTo(SUBMIT_URL));
    }

    private RequestPatternBuilder startEventRequest() {
        return getRequestedFor(urlEqualTo(START_EVENT_URL));
    }

    private CallbackRequest exceptionRecordCallbackRequest() {
        return exceptionRecordCallbackRequest(CASE_REF);
    }

    private CallbackRequest exceptionRecordCallbackRequest(String caseReference) {
        return exceptionRecordCallbackRequest(
            caseReference,
            null,
            null,
            CASE_TYPE_EXCEPTION_RECORD,
            EXCEPTION_RECORD_DOC
        );
    }

    private CallbackRequest exceptionRecordCallbackRequest(
        String attachToCaseReference,
        String searchCaseReferenceType,
        String searchCaseReference,
        String caseTypeId
    ) {
        return exceptionRecordCallbackRequest(
            attachToCaseReference,
            searchCaseReferenceType,
            searchCaseReference,
            caseTypeId,
            EXCEPTION_RECORD_DOC
        );
    }

    private CallbackRequest exceptionRecordCallbackRequest(
        String attachToCaseReference,
        String searchCaseReferenceType,
        String searchCaseReference,
        String caseTypeId,
        Map<String, Object> document
    ) {
        return CallbackRequest
            .builder()
            .caseDetails(
                exceptionRecord(
                    attachToCaseReference,
                    searchCaseReferenceType,
                    searchCaseReference,
                    caseTypeId,
                    document
                )
            )
            .eventId("attachToExistingCase")
            .build();
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

    private void mockSearchByLegacyIdToReturnTwoCases(
        String legacyId,
        String caseOneCcdId,
        String caseTwoCcdId
    ) throws IOException {
        mockCaseSearchByLegacyId(
            legacyId,
            okJson(
                getSearchResponseContent(
                    "ccd/response/search-by-legacy-id/result-format-two-cases.json",
                    caseOneCcdId,
                    caseTwoCcdId
                )
            )
        );
    }

    private String getSearchResponseContent(
        String responseFormatResourcePath,
        String... formatArgs
    ) throws IOException {
        String formatString = Resources.toString(
            Resources.getResource(responseFormatResourcePath),
            Charset.defaultCharset()
        );

        return String.format(formatString, (Object[]) formatArgs);
    }
}
