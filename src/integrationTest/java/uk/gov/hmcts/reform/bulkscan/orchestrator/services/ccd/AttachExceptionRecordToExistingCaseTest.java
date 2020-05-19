package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.collect.ImmutableList;
import io.restassured.response.ValidatableResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.IntegrationTest;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.payments.IPaymentsPublisher;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.payments.PaymentsPublishingException;
import uk.gov.hmcts.reform.ccd.client.model.CallbackRequest;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.givenThat;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.status;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.doNothing;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.config.Environment.CASE_REF;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.config.Environment.CASE_TYPE_EXCEPTION_RECORD;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.config.Environment.JURISDICTION;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification.EXCEPTION;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification.SUPPLEMENTARY_EVIDENCE_WITH_OCR;

@AutoConfigureWireMock(port = 0)
@IntegrationTest
class AttachExceptionRecordToExistingCaseTest extends AttachExceptionRecordTestBase {

    private static final String CASE_REFERENCE_TYPE_EXTERNAL = "externalCaseReference";
    private static final String CASE_REFERENCE_TYPE_CCD = "ccdCaseReference";

    @Autowired
    private IPaymentsPublisher paymentsPublisher;

    @DisplayName("Should successfully callback with correct information")
    @Test
    void should_callback_with_correct_information_when_attaching_by_attachToCaseReference() {
        CallbackRequest callbackRequest = exceptionRecordCallbackRequest(CASE_REF);

        ValidatableResponse response =
            given()
                .body(callbackRequest)
                .headers(userHeaders())
                .post(CALLBACK_ATTACH_CASE_PATH)
                .then()
                .statusCode(200);

        verifySuccessResponse(response, callbackRequest);
        verifyRequestedAttachingToCase();
    }

    @Test
    void should_callback_with_correct_information_when_attaching_by_ccd_search_case_reference() {
        CallbackRequest callbackRequest = exceptionRecordCallbackRequest(
            null,
            CASE_REFERENCE_TYPE_CCD,
            CASE_REF,
            CASE_TYPE_EXCEPTION_RECORD
        );

        ValidatableResponse response =
            given()
                .body(callbackRequest)
                .headers(userHeaders())
                .post(CALLBACK_ATTACH_CASE_PATH)
                .then()
                .statusCode(200);

        verifySuccessResponse(response, callbackRequest);
        verifyRequestedAttachingToCase();
    }

    @Test
    void should_callback_with_correct_information_when_attaching_by_legacy_id() throws Exception {
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

        CallbackRequest callbackRequest = exceptionRecordCallbackRequest(
            null,
            CASE_REFERENCE_TYPE_EXTERNAL,
            legacyId,
            CASE_TYPE_EXCEPTION_RECORD
        );

        ValidatableResponse response =
            given()
                .body(callbackRequest)
                .headers(userHeaders())
                .post(CALLBACK_ATTACH_CASE_PATH)
                .then()
                .statusCode(200);

        verifySuccessResponse(response, callbackRequest);
        verifyRequestedAttachingToCase();
    }

    @Test
    void should_callback_with_correct_information_when_all_documents_have_already_been_attached() {
        CallbackRequest callbackRequest = attachToCaseRequest(CASE_REF, null, null, EXISTING_DOC);

        ValidatableResponse response = given()
            .body(callbackRequest)
            .headers(userHeaders())
            .post(CALLBACK_ATTACH_CASE_PATH)
            .then()
            .statusCode(200);

        verifySuccessResponse(response, callbackRequest);
        verify(exactly(0), startEventRequest());
        verify(exactly(0), submittedScannedRecords());
    }

    @DisplayName("Should fail with the correct error when submit api call fails")
    @Test
    void should_fail_with_the_correct_error_when_submit_api_call_fails() {
        givenThat(ccdSubmitEvent().willReturn(status(500)));

        given()
            .body(exceptionRecordCallbackRequest())
            .headers(userHeaders())
            .post(CALLBACK_ATTACH_CASE_PATH)
            .then()
            .statusCode(500);
    }

    @DisplayName("Should fail with the correct error when start event api call fails")
    @Test
    void should_fail_with_the_correct_error_when_start_event_api_call_fails() {
        givenThat(ccdStartEvent().willReturn(status(404)));

        given()
            .body(exceptionRecordCallbackRequest())
            .headers(userHeaders())
            .post(CALLBACK_ATTACH_CASE_PATH)
            .then()
            .statusCode(500);
    }

    @Test
    void should_attach_missing_document_and_skip_already_attached_one() {
        CallbackRequest callbackRequest = CallbackRequest
            .builder()
            .caseDetails(
                CaseDetails.builder()
                    .jurisdiction(JURISDICTION)
                    .id(EXCEPTION_RECORD_ID)
                    .caseTypeId(CASE_TYPE_EXCEPTION_RECORD)
                    .data(
                        exceptionDataWithDoc(
                            ImmutableList.of(EXISTING_DOC, EXCEPTION_RECORD_DOC),
                            CASE_REF,
                            null,
                            null,
                            false
                        )
                    ).build()
            )
            .eventId(EVENT_ID_ATTACH_TO_CASE)
            .build();

        ValidatableResponse response = given()
            .body(callbackRequest)
            .headers(userHeaders())
            .post(CALLBACK_ATTACH_CASE_PATH)
            .then()
            .statusCode(200);

        verifySuccessResponse(response, callbackRequest);
        verify(exactly(1), startEventRequest());
        verify(exactly(1), submittedScannedRecords());
    }

    @Test
    void should_fail_when_duplicate_found_with_mismatching_exception_record_reference() throws JsonProcessingException {
        long exceptionRecordId = EXCEPTION_RECORD_ID - 5; // so references can mismatch against actual case doc
        mockCaseSearchByCcdId(
            String.valueOf(exceptionRecordId),
            // an exception record not attached to any case
            okJson(
                MAPPER.writeValueAsString(exceptionRecord(null))
            )
        );

        CallbackRequest callbackRequest = CallbackRequest
            .builder()
            .caseDetails(
                CaseDetails.builder()
                    .jurisdiction(JURISDICTION)
                    .id(exceptionRecordId)
                    .caseTypeId(CASE_TYPE_EXCEPTION_RECORD)
                    .data(
                        exceptionDataWithDoc(
                            ImmutableList.of(EXISTING_DOC),
                            CASE_REF,
                            null,
                            null,
                            false
                        )
                    ).build()
            )
            .eventId(EVENT_ID_ATTACH_TO_CASE)
            .build();

        given()
            .body(callbackRequest)
            .headers(userHeaders())
            .post(CALLBACK_ATTACH_CASE_PATH)
            .then()
            .statusCode(200)
            .body(RESPONSE_FIELD_ERRORS, hasItem(String.format(
                "Documents with following control numbers are already present in the case %s and cannot be added: %s",
                CASE_REF,
                DOCUMENT_NUMBER
            )));

        verify(exactly(0), startEventRequest());
        verify(exactly(0), submittedScannedRecords());
    }

    @DisplayName("Should fail correctly if the case does not exist")
    @Test
    void should_fail_when_case_referenced_by_attachToCaseReference_does_not_exist() {
        mockCaseSearchByCcdId(CASE_REF, status(404));

        given()
            .body(exceptionRecordCallbackRequest())
            .headers(userHeaders())
            .post(CALLBACK_ATTACH_CASE_PATH)
            .then()
            .statusCode(200)
            .body(RESPONSE_FIELD_ERRORS, hasItem("Could not find case: " + CASE_REF));
    }

    @Test
    void should_fail_when_case_referenced_by_ccd_search_case_reference_does_not_exist() {
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
            .headers(userHeaders())
            .post(CALLBACK_ATTACH_CASE_PATH)
            .then()
            .statusCode(200)
            .body(RESPONSE_FIELD_ERRORS, hasItem("Could not find case: " + nonExistingCaseRef));
    }

    @Test
    void should_fail_when_there_is_no_case_with_given_legacy_id() throws Exception {
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
            .headers(userHeaders())
            .post(CALLBACK_ATTACH_CASE_PATH)
            .then()
            .statusCode(200)
            .body(RESPONSE_FIELD_ERRORS, hasItem("No case found for legacy case reference " + nonExistingLegacyId));
    }

    @Test
    void should_fail_when_there_are_multiple_cases_with_given_legacy_id() throws Exception {
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
            ).headers(userHeaders())
            .post(CALLBACK_ATTACH_CASE_PATH)
            .then()
            .statusCode(200)
            .body(RESPONSE_FIELD_ERRORS, hasItem(expectedErrorMessage));
    }

    @Test
    void should_fail_correctly_if_the_case_id_is_invalid() {
        mockCaseSearchByCcdId(CASE_REF, status(400));

        given()
            .body(exceptionRecordCallbackRequest())
            .headers(userHeaders())
            .post(CALLBACK_ATTACH_CASE_PATH)
            .then()
            .statusCode(200)
            .body(RESPONSE_FIELD_ERRORS, hasItem("Invalid case ID: " + CASE_REF));
    }

    @Test
    void should_fail_when_exception_record_case_type_id_is_invalid() {
        given()
            .body(
                exceptionRecordCallbackRequest(
                    CASE_REF,
                    null,
                    null,
                    "invalid-case-type"
                )
            )
            .headers(userHeaders())
            .post(CALLBACK_ATTACH_CASE_PATH)
            .then()
            .statusCode(200)
            .body(RESPONSE_FIELD_ERRORS, hasItem("Case type ID (invalid-case-type) has invalid format"));
    }

    @Test
    void should_fail_when_search_case_reference_type_is_invalid() {
        given()
            .body(
                exceptionRecordCallbackRequest(
                    null, "invalid-reference-type",
                    "search-case-reference",
                    CASE_TYPE_EXCEPTION_RECORD
                )
            )
            .headers(userHeaders())
            .post(CALLBACK_ATTACH_CASE_PATH)
            .then()
            .statusCode(200)
            .body(RESPONSE_FIELD_ERRORS, hasItem("Invalid case reference type supplied: invalid-reference-type"));
    }

    @Test
    void should_fail_when_search_case_reference_is_invalid() {
        given()
            .body(
                exceptionRecordCallbackRequest(
                    null,
                    CASE_REFERENCE_TYPE_CCD,
                    "invalid-ccd-reference",
                    CASE_TYPE_EXCEPTION_RECORD
                )
            )
            .headers(userHeaders())
            .post(CALLBACK_ATTACH_CASE_PATH)
            .then()
            .statusCode(200)
            .body(RESPONSE_FIELD_ERRORS, hasItem("Invalid case reference: 'invalid-ccd-reference'"));
    }

    @DisplayName("Should fail correctly if ccd is down")
    @Test
    void should_fail_correctly_if_ccd_is_down() {
        mockCaseSearchByCcdId(CASE_REF, status(500));

        given()
            .body(exceptionRecordCallbackRequest(CASE_REF))
            .headers(userHeaders())
            .post(CALLBACK_ATTACH_CASE_PATH)
            .then()
            .statusCode(500);
    }

    @DisplayName("Should fail with the correct error when no case details is supplied")
    @Test
    void should_fail_with_the_correct_error_when_no_case_details_is_supplied() {
        CallbackRequest callbackRequest = exceptionRecordCallbackRequest();
        callbackRequest.setCaseDetails(null);

        given()
            .body(callbackRequest)
            .headers(userHeaders())
            .post(CALLBACK_ATTACH_CASE_PATH)
            .then()
            .statusCode(400);
    }

    @Test
    void should_fail_when_exception_record_is_already_attached_to_a_case() throws Exception {
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
            .headers(userHeaders())
            .post(CALLBACK_ATTACH_CASE_PATH)
            .then()
            .statusCode(200)
            .body(RESPONSE_FIELD_ERRORS, hasItem("Exception record is already attached to case " + caseRef));
    }

    @DisplayName("Should create error if type in incorrect")
    @Test
    void should_create_error_if_type_in_incorrect() {
        given()
            .body(exceptionRecordCallbackRequest())
            .headers(userHeaders())
            .post("/callback/someType")
            .then()
            .statusCode(404);
    }

    @Test
    void should_fail_when_event_id_is_valid_and_journey_classification_is_invalid() {
        given()
            .body(callbackRequestWith(EVENT_ID_ATTACH_TO_CASE, "invalid_classification", false))
            .headers(userHeaders())
            .post(CALLBACK_ATTACH_CASE_PATH)
            .then()
            .statusCode(200)
            .body(RESPONSE_FIELD_ERRORS, hasItem("Invalid journey classification invalid_classification"));
    }

    @Test
    void should_fail_when_callback_request_has_invalid_event_id() {
        given()
            .body(callbackRequestWith("invalid_event_id", "supplementary_evidence", false))
            .headers(userHeaders())
            .post(CALLBACK_ATTACH_CASE_PATH)
            .then()
            .statusCode(200)
            .body(
                RESPONSE_FIELD_ERRORS,
                hasItem("The invalid_event_id event is not supported. Please contact service team")
            );
    }

    @Test
    void should_fail_when_classification_is_missing_from_exception_record() {
        given()
            .body(callbackRequestWith(EVENT_ID_ATTACH_TO_CASE, null, false))
            .headers(userHeaders())
            .post(CALLBACK_ATTACH_CASE_PATH)
            .then()
            .statusCode(200)
            .body(RESPONSE_FIELD_ERRORS, hasItem("No journey classification supplied"));
    }

    @Test
    void should_fail_when_classification_is_exception_and_exception_record_has_ocr() {
        given()
            .body(callbackRequestWith(EVENT_ID_ATTACH_TO_CASE, EXCEPTION.name(), true))
            .headers(userHeaders())
            .post(CALLBACK_ATTACH_CASE_PATH)
            .then()
            .statusCode(200)
            .body(
                RESPONSE_FIELD_ERRORS,
                hasItem("The 'attach to case' event is not supported for exception records with OCR")
            );
    }

    @Test
    void should_succeed_when_classification_is_exception_and_exception_record_does_not_include_ocr() {
        CallbackRequest callbackRequest = callbackRequestWith(EVENT_ID_ATTACH_TO_CASE, EXCEPTION.name(), false);

        ValidatableResponse response =
            given()
                .body(callbackRequest)
                .headers(userHeaders())
                .post(CALLBACK_ATTACH_CASE_PATH)
                .then()
                .statusCode(200);

        verifySuccessResponse(response, callbackRequest);
        verifyRequestedAttachingToCase();
    }

    @Test
    void should_fail_when_classification_is_supplementary_evidence_with_ocr_does_not_include_ocr() {
        CallbackRequest callbackRequest =
            callbackRequestWith(
                EVENT_ID_ATTACH_TO_CASE,
                SUPPLEMENTARY_EVIDENCE_WITH_OCR.name(),
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
                hasItem("The 'attach to case' event is not supported for supplementary evidence with OCR "
                    + "but not containing OCR data")
            );
    }

    @Test
    void should_callback_with_correct_information_when_attaching_by_attachToCaseReference_with_payment() {
        CallbackRequest callbackRequest = exceptionRecordCallbackRequestWithPayment();

        doNothing().when(paymentsPublisher).send(any());
        ValidatableResponse response =
            given()
                .body(callbackRequest)
                .headers(userHeaders())
                .post(CALLBACK_ATTACH_CASE_PATH)
                .then()
                .statusCode(200);

        verifySuccessResponse(response, callbackRequest);
        verifyRequestedAttachingToCase();
    }

    @Test
    void should_fail_with_the_correct_error_when_payments_fails() {
        CallbackRequest callbackRequest = exceptionRecordCallbackRequestWithPayment();

        willThrow(new PaymentsPublishingException("Payment failed", new RuntimeException("connection")))
            .given(paymentsPublisher).send(any());

        given()
            .body(callbackRequest)
            .headers(userHeaders())
            .post(CALLBACK_ATTACH_CASE_PATH)
            .then()
            .statusCode(200)
            .body(RESPONSE_FIELD_ERRORS, hasItem("Payment references cannot be processed. Please try again later"));
    }

    private CallbackRequest callbackRequestWith(
        String eventId,
        String classification,
        boolean includeOcr
    ) {
        return CallbackRequest
            .builder()
            .caseDetails(exceptionRecordWith(classification, includeOcr))
            .eventId(eventId)
            .build();
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
            document,
            false
        );
    }

    private CallbackRequest exceptionRecordCallbackRequest() {
        return exceptionRecordCallbackRequest(CASE_REF);
    }

}
