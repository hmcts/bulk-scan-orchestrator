package uk.gov.hmcts.reform.bulkscan.orchestrator.controllers;

import io.restassured.response.ValidatableResponse;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.IntegrationTest;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CreateCaseCallbackService;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.io.IOException;

import static com.google.common.io.Resources.getResource;
import static com.google.common.io.Resources.toByteArray;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.OK;

@IntegrationTest
class CcdCallbackControllerTest {

    private static final String IDAM_TOKEN = "idam-token";

    private static final String USER_ID = "user-id";

    @LocalServerPort
    int serverPort;

    @SpyBean
    private CreateCaseCallbackService callbackService;

    @Disabled("Validation endpoint not fully implemented. Happy path not available")
    @Test
    void should_successfully_create_case() {
        // TBD - WIP
        postWithBody(getRequestBody("valid.json"))
            .statusCode(INTERNAL_SERVER_ERROR.value());

        verify(callbackService).process(any(CaseDetails.class), eq(IDAM_TOKEN), eq(USER_ID), eq("createCase"));
    }

    @Test
    void should_respond_with_relevant_error_when_body_of_create_case_callback_is_empty() {
        postWithBody("{}".getBytes())
            .statusCode(OK.value())
            .body("errors", hasItem("Internal Error: callback or case details were empty"));

        verify(callbackService, never()).process(any(CaseDetails.class), anyString(), anyString(), anyString());
    }

    @Test
    void should_respond_with_relevant_errors_when_create_case_callback_body_misses_content() {
        postWithBody(getRequestBody("invalid-empty-case-data.json"))
            .statusCode(OK.value())
            .body("errors", hasItems(
                "Missing poBox",
                "Missing journeyClassification",
                "Missing deliveryDate",
                "Missing openingDate"
            ));

        verify(callbackService).process(any(CaseDetails.class), eq(IDAM_TOKEN), eq(USER_ID), eq("createCase"));
    }

    private byte[] getRequestBody(String filename) {
        try {
            return toByteArray(getResource("ccd/callback/request/" + filename));
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }
    }

    private ValidatableResponse postWithBody(byte[] body) {
        return given()
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .header(HttpHeaders.AUTHORIZATION, IDAM_TOKEN)
            .header(CcdCallbackController.USER_ID, USER_ID)
            .body(body)
            .post("http://localhost:" + serverPort + "/callback/create-case")
            .then();
    }
}
