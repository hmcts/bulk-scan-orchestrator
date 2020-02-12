package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import io.restassured.response.ValidatableResponse;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.IntegrationTest;
import uk.gov.hmcts.reform.ccd.client.model.CallbackRequest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification.SUPPLEMENTARY_EVIDENCE;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification.SUPPLEMENTARY_EVIDENCE_WITH_OCR;

@AutoConfigureWireMock(port = 0)
@IntegrationTest
class AttachExceptionRecordWithPendingPaymentsTest extends AttachExceptionRecordTestBase {

    @Test
    void should_return_payments_error_when_config_does_not_allow_classification_with_pending_payments() {
        CallbackRequest callbackRequest =
            callbackRequestWith(
                CLASSIFICATION_EXCEPTION, // not allowed to attach exception record with pending payments
                "Yes", // awaiting payments DCN processing
                false
            );

        given()
            .body(callbackRequest)
            .headers(userHeaders())
            .post(CALLBACK_ATTACH_CASE_PATH)
            .then()
            .statusCode(200)
            .body(
                RESPONSE_FIELD_ERRORS,
                hasItem("Cannot attach this exception record to a case because it has pending payments")
            );
    }

    @Test
    void should_not_return_payments_error_when_config_allows_classification_with_pending_payments() {
        CallbackRequest callbackRequest =
            callbackRequestWith(
                SUPPLEMENTARY_EVIDENCE_WITH_OCR.name(), // config allows to attach with pending payments
                "Yes", // awaiting payments DCN processing
                true
            );

        given()
            .body(callbackRequest)
            .headers(userHeaders())
            .post(CALLBACK_ATTACH_CASE_PATH)
            .then()
            .statusCode(200)
            .body(
                RESPONSE_FIELD_ERRORS,
                not(hasItem("Cannot attach this exception record to a case because it has pending payments"))
            );
    }

    @Test
    void should_return_payments_error_when_config_does_not_allow_supplementary_evidence_with_pending_payments() {
        CallbackRequest callbackRequest =
            callbackRequestWith(
                SUPPLEMENTARY_EVIDENCE.name(), // not allowed to attach exception record with pending payments
                "Yes", // awaiting payments DCN processing
                false
            );

        given()
            .body(callbackRequest)
            .headers(userHeaders())
            .post(CALLBACK_ATTACH_CASE_PATH)
            .then()
            .statusCode(200)
            .body(
                RESPONSE_FIELD_ERRORS,
                hasItem("Cannot attach this exception record to a case because it has pending payments")
            );
    }

    @Test
    void should_attach_when_classification_is_supplementary_evidence_with_processed_payments() {
        CallbackRequest callbackRequest =
            callbackRequestWith(
                SUPPLEMENTARY_EVIDENCE.name(),
                "No", // awaiting payments DCN processing
                false
            );

        ValidatableResponse response =
            given()
                .body(callbackRequest)
                .headers(userHeaders())
                .post(CALLBACK_ATTACH_CASE_PATH)
                .then()
                .statusCode(200)
                .body(
                    RESPONSE_FIELD_ERRORS,
                    empty()
                );

        verifySuccessResponse(response, callbackRequest);
        verifyRequestedAttachingToCase();
    }

    private CallbackRequest callbackRequestWith(
        String classification,
        String awaitingPaymentDcnProcessing,
        boolean includeOcr
    ) {
        return CallbackRequest
            .builder()
            .caseDetails(exceptionRecordWith(classification, awaitingPaymentDcnProcessing, includeOcr))
            .eventId(EVENT_ID_ATTACH_TO_CASE)
            .build();
    }

}
