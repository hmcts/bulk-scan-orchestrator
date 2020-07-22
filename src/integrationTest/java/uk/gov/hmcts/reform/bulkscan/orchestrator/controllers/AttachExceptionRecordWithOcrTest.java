package uk.gov.hmcts.reform.bulkscan.orchestrator.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import io.restassured.path.json.JsonPath;
import io.restassured.response.ValidatableResponse;
import org.assertj.core.util.Maps;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.IntegrationTest;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.ExceptionRecordFields;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static com.github.tomakehurst.wiremock.client.WireMock.badRequestEntity;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.givenThat;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.google.common.io.Resources.getResource;
import static com.google.common.io.Resources.toByteArray;
import static io.restassured.RestAssured.given;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.OK;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.config.Environment.CASE_REF;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.config.Environment.CASE_TYPE_EXCEPTION_RECORD;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.config.Environment.JURISDICTION;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.ExceptionRecordFields.DISPLAY_WARNINGS;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.ExceptionRecordFields.OCR_DATA_VALIDATION_WARNINGS;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.YesNoFieldValues.NO;

@AutoConfigureWireMock(port = 0)
@IntegrationTest
class AttachExceptionRecordWithOcrTest {

    // event id used for service specific case creation
    private static final String EVENT_ID = "attachScannedDocsWithOcr";

    private static final String IDAM_TOKEN = "idam-token";
    private static final String USER_ID = "640";

    private static final String RESPONSE_FIELD_ERRORS = "errors";
    private static final String RESPONSE_FIELD_WARNINGS = "warnings";
    private static final String RESPONSE_FIELD_DATA = "data";
    private static final String SERVICE_AUTHORIZATION_HEADER = "ServiceAuthorization";
    private static final String BEARER_TOKEN_PREFIX = "Bearer";

    private static final ObjectMapper mapper = new ObjectMapper();

    private static final String CASE_ID = "1539007368674134";
    private static final String SUPPLEMENTARY_EVIDENCE_WITH_OCR = "SUPPLEMENTARY_EVIDENCE_WITH_OCR";
    private static final String ATTACH_TO_CASE_REFERENCE_FIELD_NAME = "attachToCaseReference";
    private static final String SEARCH_CASE_REFERENCE_FIELD_NAME = "searchCaseReference";
    private static final String JOURNEY_CLASSIFICATION = "journeyClassification";

    @LocalServerPort
    int serverPort;

    @DisplayName("Should successfully callback with correct information")
    @Test
    void should_update_case_with_ocr_data() throws Exception {
        setUpCaseSearchByCcdId(okJson(mapper.writeValueAsString(exceptionRecord(null))));
        setUpClientUpdate(getResponseBody("client-update-ok-no-warnings.json"));
        setUpCcdStartEvent(okJson(getResponseBody("ccd-start-event-for-case-worker.json")));
        setUpCcdSubmitEvent(okJson(getResponseBody("ccd-submit-event-for-case-worker.json")));

        Map<String, Object> caseData = getCaseData();

        byte[] requestBody = getRequestBody("valid-supplementary-evidence-with-ocr.json");

        ValidatableResponse response = postWithBody(requestBody)
            .statusCode(OK.value());

        verifySuccessResponse(response, caseData);
    }

    @DisplayName("Should successfully update the case with ocr data if document control numbers do not match")
    @Test
    void should_update_case_with_ocr_data_if_doc_numbers_not_match() throws Exception {

        CaseDetails existingCase = exceptionRecord(
            null,
            null,
            null,
            CASE_TYPE_EXCEPTION_RECORD,
            document("record.pdf", "123456"),
            false
        );

        setUpCaseSearchByCcdId(okJson(mapper.writeValueAsString(existingCase)));
        setUpClientUpdate(getResponseBody("client-update-ok-no-warnings.json"));
        setUpCcdStartEvent(okJson(getResponseBody("ccd-start-event-for-case-worker.json")));
        setUpCcdSubmitEvent(okJson(getResponseBody("ccd-submit-event-for-case-worker.json")));

        Map<String, Object> caseData = getCaseData();

        byte[] requestBody = getRequestBody("valid-supplementary-evidence-with-ocr.json");

        ValidatableResponse response = postWithBody(requestBody)
            .statusCode(OK.value());

        verifySuccessResponse(response, caseData);
    }

    @DisplayName("Should successfully pass if exception record already has same document case numbers")
    @Test
    void should_pass_if_record_already_attached_to_the_same_case() throws Exception {
        setUpCaseSearchByCcdId(okJson(mapper.writeValueAsString(exceptionRecord(null))));
        setUpCcdStartEvent(okJson(getResponseBody("ccd-start-event-for-case-worker_with_document.json")));

        Map<String, Object> caseData = getCaseData();

        byte[] requestBody = getRequestBody("valid-supplementary-evidence-with-ocr.json");

        ValidatableResponse response = postWithBody(requestBody)
            .statusCode(OK.value());

        verifySuccessResponse(response, caseData);
    }

    @DisplayName("Should fail if exception record already attached to another case")
    @Test
    void should_fail_if_record_already_attached_to_another_case() throws Exception {
        String caseRef = "1234567890123456"; // just an arbitrary value
        setUpCaseSearchByCcdId(okJson(mapper.writeValueAsString(exceptionRecord(caseRef))));

        Map<String, Object> caseData = getCaseData();

        byte[] requestBody = getRequestBody("valid-supplementary-evidence-with-ocr.json");

        ValidatableResponse response = postWithBody(requestBody)
            .statusCode(OK.value())
            .body(RESPONSE_FIELD_ERRORS, hasItem("Exception record is already attached to case " + caseRef));
    }

    @DisplayName("Should fail with the correct error when submit api call fails")
    @Test
    void should_fail_with_the_correct_error_when_submit_api_call_fails() throws Exception {
        setUpCaseSearchByCcdId(okJson(mapper.writeValueAsString(exceptionRecord(null))));
        setUpClientUpdate(getResponseBody("client-update-ok-no-warnings.json"));
        setUpCcdStartEvent(okJson(getResponseBody("ccd-start-event-for-case-worker.json")));
        setUpCcdSubmitEvent(serverError());

        byte[] requestBody = getRequestBody("valid-supplementary-evidence-with-ocr.json");

        postWithBody(requestBody).statusCode(INTERNAL_SERVER_ERROR.value());
    }

    @DisplayName("Should fail with the correct error when wrong classification")
    @Test
    void should_fail_with_the_correct_error_when_classification_is_wrong() throws Exception {
        setUpCaseSearchByCcdId(okJson(mapper.writeValueAsString(exceptionRecord(null))));
        setUpClientUpdate(getResponseBody("client-update-ok-no-warnings.json"));
        setUpCcdStartEvent(okJson(getResponseBody("ccd-start-event-for-case-worker.json")));
        setUpCcdSubmitEvent(serverError());

        byte[] requestBody = getRequestBody("wrong-classification.json");

        postWithBody(requestBody)
            .statusCode(OK.value())
            .body(RESPONSE_FIELD_ERRORS, hasItem("Invalid journey classification NEW_APPLICATION"));
    }

    @DisplayName("Should fail with the correct error when start event api call fails")
    @Test
    void should_fail_with_the_correct_error_when_start_event_api_call_fails() throws Exception {
        setUpCaseSearchByCcdId(okJson(mapper.writeValueAsString(exceptionRecord(null))));
        setUpClientUpdate(getResponseBody("client-update-ok-no-warnings.json"));
        setUpCcdStartEvent(badRequestEntity());

        byte[] requestBody = getRequestBody("valid-supplementary-evidence-with-ocr.json");

        postWithBody(requestBody).statusCode(INTERNAL_SERVER_ERROR.value());
    }

    @DisplayName("Should return with the correct error message when service case reference is invalid")
    @Test
    void should_return_correct_error_for_the_invalid_case_id() throws Exception {
        String invalidCaseId = "abc";
        setUpCaseSearchByCcdId(okJson(mapper.writeValueAsString(exceptionRecord(null))));

        // request with invalid case reference
        byte[] requestBody = getRequestBodyWithAttachToCaseRef(
            "valid-supplementary-evidence-with-ocr.json", invalidCaseId
        );

        postWithBody(requestBody)
            .statusCode(OK.value())
            .body(RESPONSE_FIELD_ERRORS, hasItem(String.format("Invalid case reference: '%s'", invalidCaseId)));
    }

    @DisplayName("Should return with the correct error message when service case doesn't exist")
    @Test
    void should_return_correct_error_when_no_case_found_for_the_case_id() throws Exception {
        String targetCaseReference = "1234123412341234";
        setUpCaseSearchByCcdId(okJson(mapper.writeValueAsString(exceptionRecord(null))));

        // request with case id which does not exist
        byte[] requestBody = getRequestBodyWithAttachToCaseRef(
            "valid-supplementary-evidence-with-ocr.json", targetCaseReference
        );

        postWithBody(requestBody)
            .statusCode(OK.value())
            .body(RESPONSE_FIELD_ERRORS, hasItem("Could not find case: " + targetCaseReference));
    }

    @DisplayName("Should fail correctly if ccd is down")
    @Test
    void should_fail_correctly_if_ccd_is_down() throws Exception {
        setUpCaseSearchByCcdId(okJson(mapper.writeValueAsString(exceptionRecord(null))));
        setUpClientUpdate(getResponseBody("client-update-ok-no-warnings.json"));
        setUpCcdStartEvent(serverError());

        byte[] requestBody = getRequestBody("valid-supplementary-evidence-with-ocr.json");

        postWithBody(requestBody).statusCode(INTERNAL_SERVER_ERROR.value());
    }

    private Map<String, Object> getCaseData() {
        Map<String, Object> caseData = new HashMap<>();
        caseData.put("poBox", "PO 12345");
        caseData.put(JOURNEY_CLASSIFICATION, SUPPLEMENTARY_EVIDENCE_WITH_OCR);
        caseData.put("formType", "B123");
        caseData.put(SEARCH_CASE_REFERENCE_FIELD_NAME, CASE_ID);
        return caseData;
    }

    private void verifySuccessResponse(
        ValidatableResponse response,
        Map<String, Object> requestData
    ) {
        JsonPath responseJson = response.extract().jsonPath();

        assertThat(responseJson.getList(RESPONSE_FIELD_ERRORS)).isNullOrEmpty();
        assertThat(responseJson.getList(RESPONSE_FIELD_WARNINGS)).isNullOrEmpty();

        Map<String, Object> responseData = responseJson.getMap(RESPONSE_FIELD_DATA);
        assertThat(responseData).isNotNull();
        assertThat(responseData.get(ATTACH_TO_CASE_REFERENCE_FIELD_NAME)).isEqualTo(CASE_REF);
        assertThat(responseData.get(DISPLAY_WARNINGS)).isEqualTo(NO);
        assertThat(responseData.get(OCR_DATA_VALIDATION_WARNINGS)).isEqualTo(emptyList());

        assertMapsAreEqualIgnoringFields(
            responseData,
            requestData,
            ATTACH_TO_CASE_REFERENCE_FIELD_NAME,
            DISPLAY_WARNINGS,
            OCR_DATA_VALIDATION_WARNINGS,
            "deliveryDate",
            "openingDate",
            "scannedDocuments",
            "scanOCRData"
        );
    }

    private void assertMapsAreEqualIgnoringFields(
        Map<String, Object> actual,
        Map<String, Object> expected,
        String... fieldsToIgnore
    ) {
        Set<String> ignoredFieldSet = Sets.newHashSet(fieldsToIgnore);

        Set<Map.Entry<String, Object>> actualWithoutIgnoredFields = getFilteredFieldSet(actual, ignoredFieldSet);
        Set<Map.Entry<String, Object>> expectedWithoutIgnoredFields = getFilteredFieldSet(expected, ignoredFieldSet);

        assertThat(actualWithoutIgnoredFields).hasSameElementsAs(expectedWithoutIgnoredFields);
    }

    private Set<Map.Entry<String, Object>> getFilteredFieldSet(
        Map<String, Object> fieldMap,
        Set<String> fieldsToExclude
    ) {
        return fieldMap
            .entrySet()
            .stream()
            .filter(e -> !fieldsToExclude.contains(e.getKey()))
            .collect(toSet());
    }

    private void setUpClientUpdate(String responseBody) {
        givenThat(post("/update-case")
            .withHeader(SERVICE_AUTHORIZATION_HEADER, containing(BEARER_TOKEN_PREFIX))
            .willReturn(okJson(responseBody))
        );
    }

    private void setUpCcdStartEvent(ResponseDefinitionBuilder response) {
        setUpCcdStartEvent(response, CASE_ID);
    }

    private void setUpCcdStartEvent(ResponseDefinitionBuilder response, String caseReference) {
        givenThat(
            get(
                "/caseworkers/" + USER_ID
                    + "/jurisdictions/BULKSCAN"
                    + "/case-types/BULKSCAN_ExceptionRecord"
                    + "/cases/" + caseReference
                    + "/event-triggers/" + EVENT_ID
                    + "/token"
            )
                .withHeader(SERVICE_AUTHORIZATION_HEADER, containing(BEARER_TOKEN_PREFIX))
                .willReturn(response)
        );
    }

    private void setUpCaseSearchByCcdId(ResponseDefinitionBuilder responseBuilder) {
        givenThat(
            get("/cases/" + CASE_ID)
                .withHeader(SERVICE_AUTHORIZATION_HEADER, containing(BEARER_TOKEN_PREFIX))
                .willReturn(responseBuilder)
        );
    }

    private void setUpCcdSubmitEvent(ResponseDefinitionBuilder response) {
        givenThat(
            post(
                // values from config + initial request body
                "/caseworkers/" + USER_ID
                    + "/jurisdictions/BULKSCAN"
                    + "/case-types/BULKSCAN_ExceptionRecord"
                    + "/cases/" + CASE_ID
                    + "/events?ignore-warning=true"
            )
                .withHeader(SERVICE_AUTHORIZATION_HEADER, containing(BEARER_TOKEN_PREFIX))
                .willReturn(response)
        );
    }

    private byte[] getFileContents(String ccdCallbackSubFolders, String filename) {
        try {
            return toByteArray(getResource("ccd/callback/attach" + ccdCallbackSubFolders + filename));
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }
    }

    private byte[] getRequestBody(String filename) {
        return getFileContents("/request/", filename);
    }

    private byte[] getRequestBodyWithAttachToCaseRef(String filename, String searchCaseReference) throws Exception {
        String fileContent = new String(getFileContents("/request/", filename));
        JSONObject json = new JSONObject(fileContent);
        JSONObject caseData = json
            .getJSONObject("case_details")
            .getJSONObject("case_data");
        caseData.put(SEARCH_CASE_REFERENCE_FIELD_NAME, searchCaseReference);
        return json.toString().getBytes();
    }

    private String getResponseBody(String filename) {
        return new String(getFileContents("/response/", filename));
    }

    private ValidatableResponse postWithBody(byte[] body) {
        return given()
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .header(HttpHeaders.AUTHORIZATION, IDAM_TOKEN)
            .header(CcdCallbackController.USER_ID, USER_ID)
            .body(body)
            .post("http://localhost:" + serverPort + "/callback/attach_case")
            .then();
    }

    private CaseDetails exceptionRecord(String attachToCaseReference) {
        final Map<String, Object> exception_record_doc = document(
            "record.pdf",
            "654321"
        );
        return exceptionRecord(
            attachToCaseReference,
            null,
            null,
            CASE_TYPE_EXCEPTION_RECORD,
            exception_record_doc,
            false
        );
    }

    private CaseDetails exceptionRecord(
        String attachToCaseReference,
        String searchCaseReferenceType,
        String searchCaseReference,
        String caseTypeId,
        Map<String, Object> document,
        boolean containsPayment
    ) {
        return CaseDetails.builder()
            .jurisdiction(JURISDICTION)
            .id(26409983479785245L)
            .caseTypeId(caseTypeId)
            .data(
                exceptionDataWithDoc(
                    document,
                    attachToCaseReference,
                    searchCaseReferenceType,
                    searchCaseReference,
                    containsPayment
                )
            ).build();
    }

    private Map<String, Object> exceptionDataWithDoc(
        Map<String, Object> scannedDocument,
        String attachToCaseReference,
        String searchCaseReferenceType,
        String searchCaseReference,
        boolean containsPayment
    ) {
        Map<String, Object> exceptionData =
            Maps.newHashMap("scannedDocuments", ImmutableList.of(scannedDocument));

        if (attachToCaseReference != null) {
            exceptionData.put(ATTACH_TO_CASE_REFERENCE_FIELD_NAME, attachToCaseReference);
        }

        if (searchCaseReferenceType != null) {
            exceptionData.put("searchCaseReferenceType", searchCaseReferenceType);
        }

        if (searchCaseReference != null) {
            exceptionData.put("searchCaseReference", searchCaseReference);
        }

        if (containsPayment) {
            exceptionData.put(ExceptionRecordFields.CONTAINS_PAYMENTS, "Yes");
            exceptionData.put(ExceptionRecordFields.ENVELOPE_ID, "21321931312-32121-312112");
            exceptionData.put(ExceptionRecordFields.PO_BOX_JURISDICTION, "sample jurisdiction");
        }

        exceptionData.put(JOURNEY_CLASSIFICATION, SUPPLEMENTARY_EVIDENCE_WITH_OCR);

        return exceptionData;
    }

    private Map<String, Object> document(String filename, String documentNumber) {
        return ImmutableMap.of(
            "value", ImmutableMap.of(
                "fileName", filename,
                "controlNumber", documentNumber,
                "someNumber", 3
            )
        );
    }
}
