package uk.gov.hmcts.reform.bulkscan.orchestrator.controllers;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import io.restassured.path.json.JsonPath;
import io.restassured.response.ValidatableResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.IntegrationTest;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.givenThat;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.google.common.io.Resources.getResource;
import static com.google.common.io.Resources.toByteArray;
import static io.restassured.RestAssured.given;
import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.OK;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.config.Environment.CASE_REF;

@IntegrationTest
class AttachExceptionRecordWithOcrTest {

    // event id used for service specific case creation
    private static final String EVENT_ID = "attachScannedDocsWithOcr";

    private static final String IDAM_TOKEN = "idam-token";
    private static final String USER_ID = "640";

    private static final String SERVICE_AUTHORIZATION_HEADER = "ServiceAuthorization";
    private static final String RESPONSE_FIELD_ERRORS = "errors";
    private static final String RESPONSE_FIELD_WARNINGS = "warnings";
    private static final String RESPONSE_FIELD_DATA = "data";
    private static final String EVENT_ID_ATTACH_TO_CASE = "attachToExistingCase";
    private static final String CLASSIFICATION_EXCEPTION = "EXCEPTION";
    private static final String CALLBACK_ATTACH_CASE_PATH = "/callback/attach_case";
    private static final String ATTACH_TO_CASE_REFERENCE_FIELD_NAME = "attachToCaseReference";

    private static final String CASE_REFERENCE_TYPE_CCD = "ccdCaseReference";
    private static final long EXCEPTION_RECORD_ID = 26409983479785245L;
    private static final String EXCEPTION_RECORD_FILENAME = "record.pdf";
    private static final String EXCEPTION_RECORD_DOCUMENT_NUMBER = "654321";
    private static final Map<String, Object> EXCEPTION_RECORD_DOC = document(
        EXCEPTION_RECORD_FILENAME,
        EXCEPTION_RECORD_DOCUMENT_NUMBER
    );

    @LocalServerPort
    int serverPort;

    @Test
    void should_update_case_with_ocr_data() throws Exception {
        setUpClientUpdate(getResponseBody("client-update-ok-no-warnings.json"));
        setUpCcdUpdateCaseEvents(
            getResponseBody("ccd-start-event-for-case-worker.json"),
            getResponseBody("ccd-submit-event-for-case-worker.json")
        );

        byte[] requestBody = getRequestBody("valid-supplementary-evidence-with-ocr.json");

        Map<String, Object> caseData = new HashMap<>();
        caseData.put("poBox","PO 12345");
        caseData.put("journeyClassification","SUPPLEMENTARY_EVIDENCE_WITH_OCR");
        caseData.put("formType","B123");
        caseData.put("attachToCaseReference","1539007368674134");

        ValidatableResponse response = postWithBody(requestBody)
            .statusCode(OK.value());

        verifySuccessResponse(response, caseData);
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

        assertMapsAreEqualIgnoringFields(
            responseData,
            requestData,
            ATTACH_TO_CASE_REFERENCE_FIELD_NAME,
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
            .withHeader("ServiceAuthorization", containing("Bearer"))
            .willReturn(okJson(responseBody))
        );
    }

    private void setUpCcdUpdateCaseEvents(String startResponseBody, String submitResponseBody) {
        givenThat(
            get(
              "/caseworkers/" + USER_ID
                + "/jurisdictions/BULKSCAN"
                + "/case-types/BULKSCAN_ExceptionRecord"
                + "/cases/1539007368674134"
                + "/event-triggers/" + EVENT_ID
                + "/token"
            )
                .withHeader("ServiceAuthorization", containing("Bearer"))
                .willReturn(okJson(startResponseBody))
        );

        givenThat(
            post(
                // values from config + initial request body
                "/caseworkers/" + USER_ID
                    + "/jurisdictions/BULKSCAN"
                    + "/case-types/BULKSCAN_ExceptionRecord"
                    + "/cases?ignore-warning=true"
            )
                .withHeader("ServiceAuthorization", containing("Bearer"))
                .willReturn(okJson(submitResponseBody))
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

    private static Map<String, Object> document(String filename, String documentNumber) {
        return ImmutableMap.of(
            "value", ImmutableMap.of(
                "fileName", filename,
                "controlNumber", documentNumber,
                "someNumber", 3
            )
        );
    }
}
