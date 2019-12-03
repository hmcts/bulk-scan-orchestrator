package uk.gov.hmcts.reform.bulkscan.orchestrator.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.restassured.response.ValidatableResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.IntegrationTest;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.givenThat;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.google.common.io.Resources.getResource;
import static com.google.common.io.Resources.toByteArray;
import static io.restassured.RestAssured.given;
import static java.util.Collections.emptyList;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.collection.IsMapWithSize.anEmptyMap;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.OK;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.EventIdValidator.EVENT_ID_CREATE_NEW_CASE;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification.EXCEPTION;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification.NEW_APPLICATION;

@IntegrationTest
class CreateCaseCallbackTest {

    // event id used for service specific case creation
    private static final String EVENT_ID = "createCase";

    private static final String IDAM_TOKEN = "idam-token";
    private static final String USER_ID = "user-id";

    @LocalServerPort
    int serverPort;

    @Test
    void should_create_case_if_classification_new_application_with_documents_and_ocr_data() throws IOException {
        setUpTransformation(getTransformationResponseBody("ok-no-warnings.json"));
        setUpCcdSearchResult(getCcdResponseBody("search-result-empty.json"));
        setUpCcdCreateCase(
            getCcdResponseBody("start-event.json"),
            getCcdResponseBody("sample-case.json")
        );

        byte[] requestBody = getRequestBody("valid-new-application-with-ocr.json");

        postWithBody(requestBody)
            .statusCode(OK.value())
            .body("errors", empty())
            .body("warnings", empty())
            .body(
                "data",
                equalTo(
                    expectedResponseExceptionRecordFields(
                        requestBody,
                        "1539007368674134" // from sample-case.json
                    )
                )
            );
    }

    @Test
    void should_not_create_case_if_classification_new_application_without_ocr_data() {
        postWithBody(getRequestBody("invalid-new-application-without-ocr.json"))
            .statusCode(OK.value())
            .body("errors", contains("Event " + EVENT_ID_CREATE_NEW_CASE + " not allowed "
                + "for the current journey classification " + NEW_APPLICATION.name() + " without OCR"))
            .body("warnings", empty())
            .body("data", anEmptyMap());
    }

    @Test
    void should_not_create_case_if_classification_exception_without_ocr_data() {
        postWithBody(getRequestBody("invalid-exception-without-ocr.json"))
            .statusCode(OK.value())
            .body("errors", contains("Event " + EVENT_ID_CREATE_NEW_CASE + " not allowed "
                + "for the current journey classification " + EXCEPTION.name() + " without OCR"))
            .body("warnings", empty())
            .body("data.", anEmptyMap());
    }

    @Test
    void should_not_create_case_if_request_specifies_to_not_ignore_warnings() {
        setUpTransformation(getTransformationResponseBody("ok-with-warnings.json"));
        setUpCcdSearchResult(getCcdResponseBody("search-result-empty.json"));

        postWithBody(getRequestBody("valid-exception-warnings-flag-on.json"))
            .statusCode(OK.value())
            .body("errors", empty())
            .body("warnings", contains("case type id looks like a number"));
    }

    @Test
    void should_create_case_if_classification_exception_with_documents_and_ocr_data() throws IOException {
        setUpTransformation(getTransformationResponseBody("ok-no-warnings.json"));
        setUpCcdSearchResult(getCcdResponseBody("search-result-empty.json"));
        setUpCcdCreateCase(
            getCcdResponseBody("start-event.json"),
            getCcdResponseBody("sample-case.json")
        );

        byte[] requestBody = getRequestBody("valid-exception.json");

        postWithBody(requestBody)
            .statusCode(OK.value())
            .body("errors", empty())
            .body("warnings", empty())
            .body(
                "data",
                equalTo(
                    expectedResponseExceptionRecordFields(
                        requestBody,
                        "1539007368674134" // from sample-case.json
                    )
                )
            );
    }

    @ParameterizedTest
    @EnumSource(
        value = HttpStatus.class,
        names = {"BAD_REQUEST", "UNPROCESSABLE_ENTITY", "BAD_GATEWAY", "INTERNAL_SERVER_ERROR"}
    )
    void should_respond_with_relevant_error_when_ccd_call_is_failing(HttpStatus responseStatus) {
        setUpTransformation(getTransformationResponseBody("ok-no-warnings.json"));
        setUpCcdSearchResult(getCcdResponseBody("search-result-empty.json"));
        setUpFailingCallToCcd(responseStatus);

        postWithBody(getRequestBody("valid-exception.json"))
            .statusCode(HttpStatus.INTERNAL_SERVER_ERROR.value())
            .body("message", equalTo("Failed to create new case"));
    }

    @ParameterizedTest
    @EnumSource(
        value = HttpStatus.class,
        names = {"BAD_REQUEST", "UNPROCESSABLE_ENTITY"}
    )
    void should_respond_with_relevant_error_when_transformation_call_is_failing(HttpStatus responseStatus) {
        setUpFailingTransformation(responseStatus);
        setUpCcdSearchResult(getCcdResponseBody("search-result-empty.json"));

        postWithBody(getRequestBody("valid-exception.json"))
            .statusCode(HttpStatus.INTERNAL_SERVER_ERROR.value())
            .body("message", equalTo("Failed to parse response"));
    }

    @ParameterizedTest
    @EnumSource(
        value = HttpStatus.class,
        names = {"INTERNAL_SERVER_ERROR"}
    )
    void should_respond_with_relevant_error_when_transformation_call_is_failing_with_internal_server_error(
        HttpStatus responseStatus
    ) {
        setUpFailingTransformation(responseStatus);
        setUpCcdSearchResult(getCcdResponseBody("search-result-empty.json"));

        postWithBody(getRequestBody("valid-exception.json"))
            .statusCode(HttpStatus.INTERNAL_SERVER_ERROR.value())
            .body("message", equalTo("Failed to create new case"));
    }

    @ParameterizedTest
    @EnumSource(
        value = HttpStatus.class,
        names = {"BAD_REQUEST", "UNPROCESSABLE_ENTITY"}
    )
    void should_respond_with_relevant_error_when_transformation_call_is_failing_with_correct_response_body(
        HttpStatus responseStatus
    ) {
        String error = "Big Error";
        String warning = "Big Warning";
        setUpFailingTransformation(responseStatus, error, warning);
        setUpCcdSearchResult(getCcdResponseBody("search-result-empty.json"));

        if (responseStatus.equals(HttpStatus.BAD_REQUEST)) {
            postWithBody(getRequestBody("valid-exception.json"))
                .statusCode(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .body("message", equalTo("Failed to transform exception record"));
        } else {
            postWithBody(getRequestBody("valid-exception.json"))
                .statusCode(OK.value())
                .body("errors", hasItem(error))
                .body("warnings", hasItem(warning));
        }
    }

    @Test
    void should_respond_with_relevant_error_when_body_of_create_case_callback_is_empty() {
        postWithBody("{}".getBytes())
            .statusCode(OK.value())
            .body("errors", hasItem("Internal Error: callback or case details were empty"));
    }

    @Test
    void should_respond_with_relevant_errors_when_create_case_callback_body_misses_content() {
        postWithBody(getRequestBody("invalid-empty-case-data.json"))
            .statusCode(OK.value())
            .body("errors", hasItems(
                "Missing poBox",
                "Missing journeyClassification",
                "Missing Form Type",
                "Missing deliveryDate",
                "Missing openingDate"
            ));
    }

    @Test
    void should_respond_with_relevant_error_when_multiple_cases_exist_for_given_exception_record() {
        setUpCcdSearchResult(getCcdResponseBody("search-result-multiple.json"));

        postWithBody(getRequestBody("valid-exception.json"))
            .statusCode(INTERNAL_SERVER_ERROR.value())
            .body("message",
                // 1539007368674134 - case id from valid-exception.json
                // 354, 456 - case ids from search-result-multiple.json
                equalTo("Multiple cases (354, 456) found for the given bulk scan case reference: 1539007368674134"));
    }

    private void setUpTransformation(String responseBody) {
        givenThat(post("/transform-exception-record")
            .withHeader("ServiceAuthorization", containing("Bearer"))
            .willReturn(okJson(responseBody))
        );
    }

    private void setUpFailingTransformation(HttpStatus responseStatus) {
        givenThat(post("/transform-exception-record")
            .withHeader("ServiceAuthorization", containing("Bearer"))
            .willReturn(aResponse().withStatus(responseStatus.value()))
        );
    }

    private void setUpFailingTransformation(HttpStatus responseStatus, String error, String warning) {
        givenThat(post("/transform-exception-record")
            .withHeader("ServiceAuthorization", containing("Bearer"))
            .willReturn(aResponse()
                .withStatus(responseStatus.value())
                .withBody("{\"errors\":[\"" + error + "\"],\"warnings\":[\"" + warning + "\"]}")
            )
        );
    }

    private void setUpCcdSearchResult(String responseBody) {
        givenThat(post("/searchCases?ctid=Bulk_Scanned")
            .withHeader("ServiceAuthorization", containing("Bearer"))
            .willReturn(okJson(responseBody))
        );
    }

    private void setUpCcdCreateCase(String startResponseBody, String submitResponseBody) {
        givenThat(
            get(
                // values from config + initial request body
                "/caseworkers/"
                    + USER_ID
                    + "/jurisdictions/BULKSCAN/case-types/123/event-triggers/"
                    + EVENT_ID
                    + "/token"
            )
                .withHeader("ServiceAuthorization", containing("Bearer"))
                .willReturn(okJson(startResponseBody))
        );

        givenThat(
            post(
                // values from config + initial request body
                "/caseworkers/" + USER_ID + "/jurisdictions/BULKSCAN/case-types/123/cases?ignore-warning=true"
            )
                .withHeader("ServiceAuthorization", containing("Bearer"))
                .willReturn(okJson(submitResponseBody))
        );
    }

    private void setUpFailingCallToCcd(HttpStatus responseStatus) {
        givenThat(
            get(
                // values from config + initial request body
                "/caseworkers/"
                    + USER_ID
                    + "/jurisdictions/BULKSCAN/case-types/123/event-triggers/"
                    + EVENT_ID
                    + "/token"
            )
                .withHeader("ServiceAuthorization", containing("Bearer"))
                .willReturn(aResponse().withStatus(responseStatus.value()))
        );
    }

    private byte[] getFileContents(String ccdCallbackSubFolders, String filename) {
        try {
            return toByteArray(getResource("ccd/callback/" + ccdCallbackSubFolders + filename));
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }
    }

    private byte[] getRequestBody(String filename) {
        return getFileContents("create-case/request/", filename);
    }

    private String getTransformationResponseBody(String filename) {
        return new String(getFileContents("transformation-client/response/", filename));
    }

    private String getCcdResponseBody(String filename) {
        return new String(getFileContents("../response/", filename));
    }

    private ValidatableResponse postWithBody(byte[] body) {
        return given()
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .header(HttpHeaders.AUTHORIZATION, IDAM_TOKEN)
            .header(CcdCallbackController.USER_ID, USER_ID)
            .body(body)
            .post("http://localhost:" + serverPort + "/callback/create-new-case")
            .then();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> expectedResponseExceptionRecordFields(
        byte[] callbackRequestBody,
        String caseReference
    ) throws IOException {
        Map<String, Object> requestBodyAsMap = new ObjectMapper().readValue(callbackRequestBody, Map.class);
        Map<String, Object> caseDetails = (Map<String, Object>) requestBodyAsMap.get("case_details");
        Map<String, Object> originalFields = (Map<String, Object>) caseDetails.get("case_data");

        Map<String, Object> expectedFields = new HashMap<>(originalFields);
        expectedFields.put("displayWarnings", "No");
        expectedFields.put("ocrDataValidationWarnings", emptyList());
        expectedFields.put("caseReference", caseReference);

        return expectedFields;
    }
}
