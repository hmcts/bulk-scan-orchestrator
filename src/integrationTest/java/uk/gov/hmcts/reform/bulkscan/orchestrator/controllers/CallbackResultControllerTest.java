package uk.gov.hmcts.reform.bulkscan.orchestrator.controllers;

import io.restassured.RestAssured;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.IntegrationTest;
import uk.gov.hmcts.reform.bulkscan.orchestrator.data.callbackresult.CallbackResult;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CallbackResultService;

import java.time.Instant;
import java.util.UUID;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.BDDMockito.given;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.OK;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.data.callbackresult.RequestType.ATTACH_TO_CASE;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.data.callbackresult.RequestType.CREATE_CASE;

@AutoConfigureWireMock(port = 0)
@IntegrationTest
public class CallbackResultControllerTest {

    private static final String CASE_ID1 = "CASE_ID1";
    private static final String CASE_ID2 = "CASE_ID2";
    private static final String EXCEPTION_RECORD_ID1 = "ER_ID1";
    private static final String EXCEPTION_RECORD_ID2 = "ER_ID2";

    @MockBean
    private CallbackResultService callbackResultService;

    @LocalServerPort
    int serverPort;

    @Test
    void should_return_callback_result_by_case_id() {
        UUID id = UUID.randomUUID();
        Instant ts = Instant.now();
        given(callbackResultService.findByCaseId(CASE_ID1))
            .willReturn(singletonList(new CallbackResult(id, ts, CREATE_CASE, EXCEPTION_RECORD_ID1, CASE_ID1)));

        RestAssured.given()
            .param("case_id", CASE_ID1)
            .get("http://localhost:" + serverPort + "/callback-results")
            .then()
            .statusCode(OK.value())
            .body("callback-results", hasSize(1))
            .body("callback-results[0].id", equalTo(id.toString()))
            .body("callback-results[0].request_type", equalTo(CREATE_CASE.toString()))
            .body("callback-results[0].exception_record_id", equalTo(EXCEPTION_RECORD_ID1))
            .body("callback-results[0].case_id", equalTo(CASE_ID1));
    }

    @Test
    void should_return_multiple_callback_results_by_case_id() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        Instant ts1 = Instant.now();
        Instant ts2 = Instant.now();
        given(callbackResultService.findByCaseId(CASE_ID1))
            .willReturn(asList(
                new CallbackResult(id1, ts1, ATTACH_TO_CASE, EXCEPTION_RECORD_ID1, CASE_ID1),
                new CallbackResult(id2, ts2, ATTACH_TO_CASE, EXCEPTION_RECORD_ID2, CASE_ID1)
            ));

        RestAssured.given()
            .param("case_id", CASE_ID1)
            .get("http://localhost:" + serverPort + "/callback-results")
            .then()
            .statusCode(OK.value())
            .body("callback-results", hasSize(2))
            .body("callback-results[0].id", equalTo(id1.toString()))
            .body("callback-results[0].request_type", equalTo(ATTACH_TO_CASE.toString()))
            .body("callback-results[0].exception_record_id", equalTo(EXCEPTION_RECORD_ID1))
            .body("callback-results[0].case_id", equalTo(CASE_ID1))
            .body("callback-results[1].id", equalTo(id2.toString()))
            .body("callback-results[1].request_type", equalTo(ATTACH_TO_CASE.toString()))
            .body("callback-results[1].exception_record_id", equalTo(EXCEPTION_RECORD_ID2))
            .body("callback-results[1].case_id", equalTo(CASE_ID1));
    }

    @Test
    void should_return_empty_result_by_case_id_if_no_callback_results_found() {
        UUID id = UUID.randomUUID();
        Instant ts = Instant.now();
        given(callbackResultService.findByCaseId(CASE_ID1)).willReturn(emptyList());

        RestAssured.given()
            .param("case_id", CASE_ID1)
            .get("http://localhost:" + serverPort + "/callback-results")
            .then()
            .statusCode(OK.value())
            .body("callback-results", hasSize(0));
    }

    @Test
    void should_return_callback_result_by_exception_record_id() {
        UUID id = UUID.randomUUID();
        Instant ts = Instant.now();
        given(callbackResultService.findByExceptionRecordId(EXCEPTION_RECORD_ID1))
            .willReturn(singletonList(new CallbackResult(id, ts, CREATE_CASE, EXCEPTION_RECORD_ID1, CASE_ID1)));

        RestAssured.given()
            .param("exception_record_id", EXCEPTION_RECORD_ID1)
            .get("http://localhost:" + serverPort + "/callback-results")
            .then()
            .statusCode(OK.value())
            .body("callback-results", hasSize(1))
            .body("callback-results[0].id", equalTo(id.toString()))
            .body("callback-results[0].request_type", equalTo(CREATE_CASE.toString()))
            .body("callback-results[0].exception_record_id", equalTo(EXCEPTION_RECORD_ID1))
            .body("callback-results[0].case_id", equalTo(CASE_ID1));
    }

    @Test
    void should_return_multiple_callback_results_by_exception_record_id() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        Instant ts1 = Instant.now();
        Instant ts2 = Instant.now();
        given(callbackResultService.findByExceptionRecordId(EXCEPTION_RECORD_ID1))
            .willReturn(asList(
                new CallbackResult(id1, ts1, CREATE_CASE, EXCEPTION_RECORD_ID1, CASE_ID1),
                new CallbackResult(id2, ts2, ATTACH_TO_CASE, EXCEPTION_RECORD_ID1, CASE_ID2)
            ));

        RestAssured.given()
            .param("exception_record_id", EXCEPTION_RECORD_ID1)
            .get("http://localhost:" + serverPort + "/callback-results")
            .then()
            .statusCode(OK.value())
            .body("callback-results", hasSize(2))
            .body("callback-results[0].id", equalTo(id1.toString()))
            .body("callback-results[0].request_type", equalTo(CREATE_CASE.toString()))
            .body("callback-results[0].exception_record_id", equalTo(EXCEPTION_RECORD_ID1))
            .body("callback-results[0].case_id", equalTo(CASE_ID1))
            .body("callback-results[1].id", equalTo(id2.toString()))
            .body("callback-results[1].request_type", equalTo(ATTACH_TO_CASE.toString()))
            .body("callback-results[1].exception_record_id", equalTo(EXCEPTION_RECORD_ID1))
            .body("callback-results[1].case_id", equalTo(CASE_ID2));
    }

    @Test
    void should_return_empty_result_by_exception_record_id_if_no_callback_results_found() {
        UUID id = UUID.randomUUID();
        Instant ts = Instant.now();
        given(callbackResultService.findByExceptionRecordId(EXCEPTION_RECORD_ID1)).willReturn(emptyList());

        RestAssured.given()
            .param("exception_record_id", EXCEPTION_RECORD_ID1)
            .get("http://localhost:" + serverPort + "/callback-results")
            .then()
            .statusCode(OK.value())
            .body("callback-results", hasSize(0));
    }

    @Test
    void should_return_bad_request_if_both_parameters_are_present() {
        RestAssured.given()
            .param("case_id", CASE_ID1)
            .param("exception_record_id", EXCEPTION_RECORD_ID1)
            .get("http://localhost:" + serverPort + "/callback-results")
            .then()
            .statusCode(BAD_REQUEST.value())
            .body("message",
                equalTo("Request should have exactly one parameter 'case_id' or 'exception_record_id'"));
    }

    @Test
    void should_return_bad_request_if_no_parameter_is_present() {
        RestAssured.given()
            .get("http://localhost:" + serverPort + "/callback-results")
            .then()
            .statusCode(BAD_REQUEST.value())
            .body("message",
                equalTo("Request should have exactly one parameter 'case_id' or 'exception_record_id'"));
    }
}
