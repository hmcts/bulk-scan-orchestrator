package uk.gov.hmcts.reform.bulkscan.orchestrator.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import io.restassured.path.json.JsonPath;
import io.restassured.response.ValidatableResponse;
import org.assertj.core.util.Maps;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.IntegrationTest;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.ExceptionRecordFields;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.givenThat;
import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.google.common.io.Resources.getResource;
import static com.google.common.io.Resources.toByteArray;
import static io.restassured.RestAssured.given;
import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.OK;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.config.Environment.CASE_REF;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.config.Environment.CASE_TYPE_EXCEPTION_RECORD;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.config.Environment.JURISDICTION;

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
    private static final String BEARER = "Bearer";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final long EXCEPTION_RECORD_ID = 26409983479785245L;
    private static final String EXCEPTION_RECORD_FILENAME = "record.pdf";
    private static final String EXCEPTION_RECORD_DOCUMENT_NUMBER = "654321";

    private static final Map<String, Object> EXCEPTION_RECORD_DOC = document(
        EXCEPTION_RECORD_FILENAME,
        EXCEPTION_RECORD_DOCUMENT_NUMBER
    );
    private static final String CASE_ID = "1539007368674134";
    private static final String SUPPLEMENTARY_EVIDENCE_WITH_OCR = "SUPPLEMENTARY_EVIDENCE_WITH_OCR";
    private static final String PO_BOX_VALUE = "PO 12345";
    private static final String FORM_TYPE_VALUE = "B123";
    private static final String ATTACH_TO_CASE_REFERENCE = "attachToCaseReference";
    private static final String FORM_TYPE = "formType";
    private static final String JOURNEY_CLASSIFICATION = "journeyClassification";
    private static final String PO_BOX = "poBox";
    private static final String SUPPLEMENTARY_EVIDENCE = "SUPPLEMENTARY_EVIDENCE";
    private static final String SEARCH_CASE_REFERENCE = "searchCaseReference";
    private static final String SEARCH_CASE_REFERENCE_TYPE = "searchCaseReferenceType";
    private static final String SCANNED_DOCUMENTS = "scannedDocuments";
    private static final String CONTAINS_PAYMENTS = "containsPayments";

    private static Map<String, Object> document(String filename, String documentNumber) {
        return ImmutableMap.of(
            "value", ImmutableMap.of(
                "fileName", filename,
                "controlNumber", documentNumber,
                "someNumber", 3
            )
        );
    }

    @LocalServerPort
    int serverPort;

    @DisplayName("Should successfully callback with correct information")
    @Test
    void should_update_case_with_ocr_data() throws JsonProcessingException {
        setUpCaseSearchByCcdId(okJson(MAPPER.writeValueAsString(exceptionRecord(null))));
        setUpClientUpdate(getResponseBody("client-update-ok-no-warnings.json"));
        setUpCcdStartEvent(okJson(getResponseBody("ccd-start-event-for-case-worker.json")));
        setUpCcdSubmitEvent(okJson(getResponseBody("ccd-submit-event-for-case-worker.json")));

        Map<String, Object> caseData = getCaseData();

        byte[] requestBody = getRequestBody("valid-supplementary-evidence-with-ocr.json");

        ValidatableResponse response = postWithBody(requestBody)
            .statusCode(OK.value());

        verifySuccessResponse(response, caseData);
    }

    @DisplayName("Should successfully pass if exception record already attached")
    @Test
    void should_pass_if_record_already_attached() throws JsonProcessingException {
        String caseRef = "1234567890123456"; // just an arbitrary value
        setUpCaseSearchByCcdId(okJson(MAPPER.writeValueAsString(exceptionRecord(caseRef))));

        Map<String, Object> caseData = getCaseData();

        byte[] requestBody = getRequestBody("valid-supplementary-evidence-with-ocr.json");

        ValidatableResponse response = postWithBody(requestBody)
            .statusCode(OK.value());

        verifySuccessResponse(response, caseData);
    }

    @DisplayName("Should fail with the correct error when submit api call fails")
    @Test
    void should_fail_with_the_correct_error_when_submit_api_call_fails() throws JsonProcessingException {
        setUpCaseSearchByCcdId(okJson(MAPPER.writeValueAsString(exceptionRecord(null))));
        setUpClientUpdate(getResponseBody("client-update-ok-no-warnings.json"));
        setUpCcdStartEvent(okJson(getResponseBody("ccd-start-event-for-case-worker.json")));
        setUpCcdSubmitEvent(serverError());

        byte[] requestBody = getRequestBody("valid-supplementary-evidence-with-ocr.json");

        postWithBody(requestBody).statusCode(INTERNAL_SERVER_ERROR.value());
    }

    @DisplayName("Should fail with the correct error when wrong classification")
    @Test
    void should_fail_with_the_correct_error_when_classification_is_wrong() throws JsonProcessingException {
        setUpCaseSearchByCcdId(okJson(MAPPER.writeValueAsString(exceptionRecord(null))));
        setUpClientUpdate(getResponseBody("client-update-ok-no-warnings.json"));
        setUpCcdStartEvent(okJson(getResponseBody("ccd-start-event-for-case-worker.json")));
        setUpCcdSubmitEvent(serverError());

        byte[] requestBody = getRequestBody("wrong-classification.json");

        postWithBody(requestBody)
            .statusCode(200)
            .body(RESPONSE_FIELD_ERRORS, hasItem("Invalid journey classification NEW_APPLICATION"));
    }

    @DisplayName("Should fail with the correct error when start event api call fails")
    @Test
    void should_fail_with_the_correct_error_when_start_event_api_call_fails() throws JsonProcessingException {
        setUpCaseSearchByCcdId(okJson(MAPPER.writeValueAsString(exceptionRecord(null))));
        setUpClientUpdate(getResponseBody("client-update-ok-no-warnings.json"));
        setUpCcdStartEvent(notFound());

        byte[] requestBody = getRequestBody("valid-supplementary-evidence-with-ocr.json");

        postWithBody(requestBody).statusCode(INTERNAL_SERVER_ERROR.value());
    }

    @DisplayName("Should fail correctly if ccd is down")
    @Test
    void should_fail_correctly_if_ccd_is_down() throws JsonProcessingException {
        setUpCaseSearchByCcdId(okJson(MAPPER.writeValueAsString(exceptionRecord(null))));
        setUpClientUpdate(getResponseBody("client-update-ok-no-warnings.json"));
        setUpCcdStartEvent(serverError());

        byte[] requestBody = getRequestBody("valid-supplementary-evidence-with-ocr.json");

        postWithBody(requestBody).statusCode(INTERNAL_SERVER_ERROR.value());
    }

    private Map<String, Object> getCaseData() {
        Map<String, Object> caseData = new HashMap<>();
        caseData.put(PO_BOX, PO_BOX_VALUE);
        caseData.put(JOURNEY_CLASSIFICATION, SUPPLEMENTARY_EVIDENCE_WITH_OCR);
        caseData.put(FORM_TYPE, FORM_TYPE_VALUE);
        caseData.put(ATTACH_TO_CASE_REFERENCE, CASE_ID);
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
        assertThat(responseData.get(ATTACH_TO_CASE_REFERENCE)).isEqualTo(CASE_REF);

        assertMapsAreEqualIgnoringFields(
            responseData,
            requestData,
            ATTACH_TO_CASE_REFERENCE,
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
            .withHeader(SERVICE_AUTHORIZATION_HEADER, containing(BEARER))
            .willReturn(okJson(responseBody))
        );
    }

    private void setUpCcdStartEvent(ResponseDefinitionBuilder response) {
        givenThat(
            get(
              "/caseworkers/" + USER_ID
                + "/jurisdictions/BULKSCAN"
                + "/case-types/BULKSCAN_ExceptionRecord"
                + "/cases/" + CASE_ID
                + "/event-triggers/" + EVENT_ID
                + "/token"
            )
                .withHeader(SERVICE_AUTHORIZATION_HEADER, containing(BEARER))
                .willReturn(response)
        );
    }

    private void setUpCaseSearchByCcdId(ResponseDefinitionBuilder responseBuilder) throws JsonProcessingException {
        givenThat(
            get("/cases/" + CASE_ID)
                .withHeader(SERVICE_AUTHORIZATION_HEADER, containing(BEARER))
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
                .withHeader(SERVICE_AUTHORIZATION_HEADER, containing(BEARER))
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
        return exceptionRecord(
            attachToCaseReference,
            null,
            null,
            CASE_TYPE_EXCEPTION_RECORD,
            EXCEPTION_RECORD_DOC,
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
            .id(EXCEPTION_RECORD_ID)
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
            Maps.newHashMap(SCANNED_DOCUMENTS, ImmutableList.of(scannedDocument));

        if (attachToCaseReference != null) {
            exceptionData.put(ATTACH_TO_CASE_REFERENCE, attachToCaseReference);
        }

        if (searchCaseReferenceType != null) {
            exceptionData.put(SEARCH_CASE_REFERENCE_TYPE, searchCaseReferenceType);
        }

        if (searchCaseReference != null) {
            exceptionData.put(SEARCH_CASE_REFERENCE, searchCaseReference);
        }

        if (containsPayment) {
            exceptionData.put(ExceptionRecordFields.CONTAINS_PAYMENTS, "Yes");
            exceptionData.put(ExceptionRecordFields.ENVELOPE_ID, "21321931312-32121-312112");
            exceptionData.put(ExceptionRecordFields.PO_BOX_JURISDICTION, "sample jurisdiction");
        }

        exceptionData.put(JOURNEY_CLASSIFICATION, SUPPLEMENTARY_EVIDENCE);

        return exceptionData;
    }
}
