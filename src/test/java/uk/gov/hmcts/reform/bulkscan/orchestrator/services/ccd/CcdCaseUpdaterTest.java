package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import feign.FeignException;
import feign.Request;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.BDDMockito.BDDMyOngoingStubbing;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGenerator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.ServiceResponseParser;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.caseupdate.model.response.CaseUpdateDetails;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.caseupdate.model.response.SuccessfulUpdateResponse;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.request.DocumentType;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.request.DocumentUrl;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.request.ScannedDocument;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.response.ClientServiceErrorResponse;
import uk.gov.hmcts.reform.bulkscan.orchestrator.helper.CaseDataUpdater;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.CaseAction;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.internal.ExceptionRecord;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.caseupdatedetails.CaseUpdateDetailsService;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.callback.CallbackException;
import uk.gov.hmcts.reform.ccd.client.model.CaseDataContent;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.ccd.client.model.StartEventResponse;

import java.util.Map;
import java.util.Optional;
import jakarta.validation.ConstraintViolationException;

import static java.time.LocalDateTime.now;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification.SUPPLEMENTARY_EVIDENCE_WITH_OCR;

@SuppressWarnings({"checkstyle:LineLength"})
@ExtendWith(MockitoExtension.class)
class CcdCaseUpdaterTest {

    private static final String EXISTING_CASE_ID = "existing_case_id";
    private static final String EXISTING_CASE_TYPE_ID = "existing_case_type";
    private static final String SERVICE_NAME = "some_service_name";

    private CcdCaseUpdater ccdCaseUpdater;

    @Mock private CaseUpdateDetailsService caseUpdateDataService;
    @Mock private ServiceResponseParser serviceResponseParser;
    @Mock private AuthTokenGenerator authTokenGenerator;
    @Mock private CcdApi ccdApi;
    @Mock private CaseDetails existingCase;
    @Mock private StartEventResponse eventResponse;
    @Mock private CaseDataUpdater caseDataUpdater;
    @Mock private EnvelopeReferenceHelper envelopeReferenceHelper;

    private ExceptionRecord exceptionRecord;

    private SuccessfulUpdateResponse noWarningsUpdateResponse;
    private SuccessfulUpdateResponse warningsUpdateResponse;

    private CaseUpdateDetails caseUpdateDetails;
    private Map<String, Object> caseDataAfterDocExceptionRefUpdate;

    @BeforeEach
    void setUp() {
        ccdCaseUpdater = new CcdCaseUpdater(
            authTokenGenerator,
            ccdApi,
            caseUpdateDataService,
            caseDataUpdater,
            envelopeReferenceHelper,
            serviceResponseParser
        );

        caseUpdateDetails = new CaseUpdateDetails(null, Map.of("a", "b"));
        caseDataAfterDocExceptionRefUpdate = Map.of("x", "y");
        exceptionRecord = getExceptionRecord();

        noWarningsUpdateResponse =
            new SuccessfulUpdateResponse(caseUpdateDetails, emptyList());
        warningsUpdateResponse =
            new SuccessfulUpdateResponse(caseUpdateDetails, singletonList("warning1"));

        given(authTokenGenerator.generate()).willReturn("token");
    }

    @Test
    void updateCase_should_return_no_error_or_warnings_if_no_warnings_from_updateCase() {
        // given
        given(caseUpdateDataService.getCaseUpdateData(
            SERVICE_NAME,
            existingCase, exceptionRecord
        ))
            .willReturn(noWarningsUpdateResponse);
        initResponseMockData();
        initMockData();
        prepareMockForSubmissionEventForCaseWorker()
            .willReturn(CaseDetails.builder().id(1L).build());

        // when
        Optional<ErrorsAndWarnings> res = ccdCaseUpdater.updateCase(
            exceptionRecord,
            SERVICE_NAME,
            false,
            "idamToken",
            "userId",
            EXISTING_CASE_ID,
            EXISTING_CASE_TYPE_ID
        );

        // then
        assertThat(res).isEmpty();

        verify(caseDataUpdater)
            .setExceptionRecordIdAndHashTokenToScannedDocuments(
                exceptionRecord,
                caseUpdateDetails.caseData
            );
        verify(caseDataUpdater, never())
            .updateEnvelopeReferences(any(), any(), any(),  any());
    }

    @Test
    void should_update_envelope_reference_if_its_enabled_for_service() {
        given(envelopeReferenceHelper
            .serviceSupportsEnvelopeReferences(any())).willReturn(true);
        given(caseDataUpdater
            .setExceptionRecordIdAndHashTokenToScannedDocuments(
                exceptionRecord,
                caseUpdateDetails.caseData
            ))
            .willReturn(caseDataAfterDocExceptionRefUpdate);

        given(caseUpdateDataService.getCaseUpdateData(
            SERVICE_NAME,
            existingCase,
            exceptionRecord
        ))
            .willReturn(noWarningsUpdateResponse);
        initResponseMockData();
        initMockData();
        prepareMockForSubmissionEventForCaseWorker()
            .willReturn(CaseDetails.builder().id(1L).build());

        Map<String, Object>  existingCaseData = Map.of();
        given(existingCase.getData()).willReturn(existingCaseData);

        // when
        ccdCaseUpdater.updateCase(
            exceptionRecord,
            SERVICE_NAME,
            false,
            "idamToken",
            "userId",
            EXISTING_CASE_ID,
            EXISTING_CASE_TYPE_ID
        );

        // then
        verify(caseDataUpdater)
            .setExceptionRecordIdAndHashTokenToScannedDocuments(
                exceptionRecord,
                caseUpdateDetails.caseData
            );
        verify(caseDataUpdater).updateEnvelopeReferences(
            caseDataAfterDocExceptionRefUpdate,
            exceptionRecord.envelopeId,
            CaseAction.UPDATE,
            existingCaseData
        );
    }

    @Test
    void updateCase_should_return_no_error_or_warnings_if_warnings_from_updateCase_and_ignoreWarnings_is_true() {
        // given
        given(caseUpdateDataService.getCaseUpdateData(
            SERVICE_NAME,
            existingCase,
            exceptionRecord
        ))
            .willReturn(warningsUpdateResponse);
        initResponseMockData();
        initMockData();
        prepareMockForSubmissionEventForCaseWorker()
            .willReturn(CaseDetails.builder().id(1L).build());

        // when
        Optional<ErrorsAndWarnings> res = ccdCaseUpdater.updateCase(
            exceptionRecord,
            SERVICE_NAME,
            true,
            "idamToken",
            "userId",
            EXISTING_CASE_ID,
            EXISTING_CASE_TYPE_ID
        );

        // then
        assertThat(res).isEmpty();

        verify(caseDataUpdater)
            .setExceptionRecordIdAndHashTokenToScannedDocuments(
                exceptionRecord,
                caseUpdateDetails.caseData
            );
    }

    @Test
    void updateCase_should_return_warnings_if_warnings_from_updateCase_and_ignoreWarnings_is_false() {
        // given
        given(caseUpdateDataService.getCaseUpdateData(
            SERVICE_NAME,
            existingCase,
            exceptionRecord
        ))
            .willReturn(warningsUpdateResponse);
        initMockData();

        // when
        Optional<ErrorsAndWarnings> res = ccdCaseUpdater.updateCase(
            exceptionRecord,
            SERVICE_NAME,
            false,
            "idamToken",
            "userId",
            EXISTING_CASE_ID,
            EXISTING_CASE_TYPE_ID
        );

        // then
        assertThat(res).isNotEmpty();
        assertThat(res.get().getErrors()).isEmpty();
        assertThat(res.get().getWarnings()).containsOnly("warning1");

        verifyNoInteractions(caseDataUpdater);
    }

    @Test
    void updateCase_should_handle_conflict_response_from_ccd_api() {
        initResponseMockData();
        given(caseUpdateDataService.getCaseUpdateData(
            anyString(),
            any(CaseDetails.class),
            any(ExceptionRecord.class)
        ))
            .willReturn(noWarningsUpdateResponse);
        initMockData();
        prepareMockForSubmissionEventForCaseWorker()
            .willThrow(new FeignException.Conflict(
                "Msg",
                mock(Request.class),
                "Body".getBytes(),
                null
            ));

        // when
        Optional<ErrorsAndWarnings> res = ccdCaseUpdater.updateCase(
            exceptionRecord,
            SERVICE_NAME,
            true,
            "idamToken",
            "userId",
            EXISTING_CASE_ID,
            EXISTING_CASE_TYPE_ID
        );

        // then
        assertThat(res).isNotEmpty();
        assertThat(res.get().getErrors()).containsExactlyInAnyOrder(
            "Failed to update case for " + SERVICE_NAME + " service with case Id "
                + EXISTING_CASE_ID + " based on exception record "
                + exceptionRecord.id + " because it has been updated in the meantime"
        );
        assertThat(res.get().getWarnings()).isEmpty();

        verify(caseDataUpdater)
            .setExceptionRecordIdAndHashTokenToScannedDocuments(
                exceptionRecord,
                caseUpdateDetails.caseData
            );
    }

    @Test
    void updateCase_should_handle_feign_exception() {
        // given
        initResponseMockData();
        given(caseUpdateDataService.getCaseUpdateData(
            anyString(),
            any(CaseDetails.class),
            any(ExceptionRecord.class)
        ))
            .willReturn(noWarningsUpdateResponse);
        initMockData();

        var ccdException = mock(FeignException.BadRequest.class);
        final CcdCallException ex =
            new CcdCallException("Service response: Body", ccdException);
        prepareMockForSubmissionEventForCaseWorker().willThrow(ex);

        // when
        CallbackException callbackException = catchThrowableOfType(
            () ->
                ccdCaseUpdater.updateCase(
                    exceptionRecord,
                    SERVICE_NAME,
                    true,
                    "idamToken",
                    "userId",
                    EXISTING_CASE_ID,
                    EXISTING_CASE_TYPE_ID
                ),
            CallbackException.class
        );

        // then
        assertThat(callbackException.getMessage())
            .isEqualTo(
                "Failed to update case for " + SERVICE_NAME + " service with case Id "
                    + EXISTING_CASE_ID + " based on exception record " + exceptionRecord.id);
        assertThat(callbackException.getCause()).isSameAs(ex);

        verify(caseDataUpdater)
            .setExceptionRecordIdAndHashTokenToScannedDocuments(
                exceptionRecord,
                caseUpdateDetails.caseData
            );
    }

    @Test
    void updateCase_should_handle_feign_unprocessable_entity() {
        // given
        initResponseMockData();
        given(caseUpdateDataService.getCaseUpdateData(
            anyString(),
            any(CaseDetails.class),
            any(ExceptionRecord.class)
        ))
            .willReturn(noWarningsUpdateResponse);
        initMockData();
        prepareMockForSubmissionEventForCaseWorker()
            .willThrow(
                new CcdCallException(
                    "ErrMsg",
                    new FeignException.UnprocessableEntity(
                        "Msg",
                        mock(Request.class),
                        "Body".getBytes(),
                        null
                    )
                )
            );

        // when
        CallbackException exception = catchThrowableOfType(
            () ->
                ccdCaseUpdater.updateCase(
                    exceptionRecord,
                    SERVICE_NAME,
                    true,
                    "idamToken",
                    "userId",
                    EXISTING_CASE_ID,
                    EXISTING_CASE_TYPE_ID
                ),
            CallbackException.class
        );

        // then
        assertThat(exception)
            .hasCauseInstanceOf(RuntimeException.class)
            .hasMessage(
                "Failed to update case for %s service with case Id %s based on exception record %s",
                SERVICE_NAME,
                EXISTING_CASE_ID,
                exceptionRecord.id
            );

        verify(caseDataUpdater)
            .setExceptionRecordIdAndHashTokenToScannedDocuments(
                exceptionRecord,
                caseUpdateDetails.caseData
            );
    }

    @Test
    void updateCase_should_handle_rest_template_client_exception_for_i_o_exceptions() {
        // given
        given(existingCase.getCaseTypeId()).willReturn("caseTypeId");
        given(eventResponse.getEventId())
            .willReturn(EventIds.ATTACH_SCANNED_DOCS_WITH_OCR);
        given(caseUpdateDataService.getCaseUpdateData(
            anyString(),
            any(CaseDetails.class),
            any(ExceptionRecord.class)
        ))
            .willThrow(new RestClientException("I/O error"));
        initMockData();

        // when
        CallbackException exception = catchThrowableOfType(
            () ->
                ccdCaseUpdater.updateCase(
                    exceptionRecord,
                    SERVICE_NAME,
                    true,
                    "idamToken",
                    "userId",
                    EXISTING_CASE_ID,
                    EXISTING_CASE_TYPE_ID
                ),
            CallbackException.class
        );

        // then
        assertThat(exception)
            .hasCauseInstanceOf(RestClientException.class)
            .hasMessage(
                "Failed to update case for %s service with case Id %s based on exception record %s",
                SERVICE_NAME,
                EXISTING_CASE_ID,
                exceptionRecord.id
            );

        verifyNoInteractions(caseDataUpdater);
    }

    @Test
    void updateCase_should_handle_response_validation_exception() {
        // given
        given(existingCase.getCaseTypeId()).willReturn("caseTypeId");
        given(eventResponse.getEventId())
            .willReturn(EventIds.ATTACH_SCANNED_DOCS_WITH_OCR);
        given(caseUpdateDataService.getCaseUpdateData(
            anyString(),
            any(CaseDetails.class),
            any(ExceptionRecord.class)
        ))
            .willThrow(new ConstraintViolationException("validation error message", emptySet()));
        initMockData();

        // when
        Throwable exception = catchThrowable(
            () ->
                ccdCaseUpdater.updateCase(
                    exceptionRecord,
                    SERVICE_NAME,
                    true,
                    "idamToken",
                    "userId",
                    EXISTING_CASE_ID,
                    EXISTING_CASE_TYPE_ID
                )
        );

        // then
        assertThat(exception)
            .isInstanceOf(CallbackException.class)
            .hasCauseInstanceOf(ConstraintViolationException.class)
            .hasMessageContaining("Invalid case-update response");

        verifyNoInteractions(caseDataUpdater);
    }

    @Test
    void updateCase_should_handle_bad_request_from_start_event() {
        // given
        given(ccdApi.startEventForCaseWorker(
            any(CcdRequestCredentials.class),
            anyString(),
            anyString(),
            anyString(),
            anyString()
        ))
            .willThrow(HttpClientErrorException.create(
                HttpStatus.BAD_REQUEST,
                "bad request message",
                HttpHeaders.EMPTY,
                null,
                null
            ));

        // when
        CallbackException callbackException = catchThrowableOfType(
            () ->
                ccdCaseUpdater.updateCase(
                    exceptionRecord,
                    SERVICE_NAME,
                    true,
                    "idamToken",
                    "userId",
                    EXISTING_CASE_ID,
                    EXISTING_CASE_TYPE_ID
                ),
            CallbackException.class
        );

        // then
        assertThat(callbackException.getMessage())
            .isEqualTo(
                "Failed to update case for " + SERVICE_NAME + " service with case Id "
                    + EXISTING_CASE_ID + " based on exception record " + exceptionRecord.id
            );
        assertThat(callbackException.getCause().getMessage())
            .isEqualTo("400 bad request message");
        assertThat(callbackException.getCause()).isInstanceOf(HttpClientErrorException.class);
        assertThat(((HttpClientErrorException) callbackException.getCause()).getStatusText())
            .isEqualTo("bad request message");
        assertThat(((HttpClientErrorException) callbackException.getCause()).getRawStatusCode())
            .isEqualTo(400);

        verifyNoInteractions(caseDataUpdater);
    }

    @Test
    void updateCase_should_handle_unprocessable_entity() {
        // given
        final HttpClientErrorException unprocessableEntity =
            HttpClientErrorException.create(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "unprocessable entity message",
                HttpHeaders.EMPTY,
                null,
                null
            );
        given(ccdApi.startEventForCaseWorker(
            any(CcdRequestCredentials.class),
            anyString(),
            anyString(),
            anyString(),
            anyString()
        ))
            .willThrow(unprocessableEntity);
        given(serviceResponseParser.parseResponseBody(unprocessableEntity))
            .willReturn(new ClientServiceErrorResponse(
                asList("error1", "error2"),
                emptyList()
            ));

        // when
        Optional<ErrorsAndWarnings> res = ccdCaseUpdater.updateCase(
            exceptionRecord,
            SERVICE_NAME,
            true,
            "idamToken",
            "userId",
            EXISTING_CASE_ID,
            EXISTING_CASE_TYPE_ID
        );

        // then
        assertThat(res).isNotEmpty();
        assertThat(res.get().getErrors()).containsExactlyInAnyOrder("error1", "error2");
        assertThat(res.get().getWarnings()).isEmpty();

        verifyNoInteractions(caseDataUpdater);
    }

    @Test
    void updateCase_should_handle_feign_exception_from_start_event() {
        // given
        given(ccdApi.startEventForCaseWorker(
            any(CcdRequestCredentials.class),
            anyString(),
            anyString(),
            anyString(),
            anyString()
        ))
            .willThrow(new FeignException.MethodNotAllowed(
                "Msg",
                mock(Request.class),
                "Body".getBytes(),
                null
            ));

        // when
        CallbackException callbackException = catchThrowableOfType(
            () ->
                ccdCaseUpdater.updateCase(
                    exceptionRecord,
                    SERVICE_NAME,
                    true,
                    "idamToken",
                    "userId",
                    EXISTING_CASE_ID,
                    EXISTING_CASE_TYPE_ID
                ),
            CallbackException.class
        );

        // then
        assertThat(callbackException.getMessage())
            .isEqualTo(
                "Failed to update case for " + SERVICE_NAME + " service with case Id "
                    + EXISTING_CASE_ID + " based on exception record "
                    + exceptionRecord.id + ". Service response: Body"
            );
        assertThat(callbackException.getCause().getMessage()).isEqualTo("Msg");

        verifyNoInteractions(caseDataUpdater);
    }

    @Test
    void updateCase_should_handle_generic_exception() {
        // given
        given(ccdApi.startEventForCaseWorker(
            any(CcdRequestCredentials.class),
            anyString(),
            anyString(),
            anyString(),
            anyString()
        ))
            .willThrow(new RuntimeException());

        // when
        CallbackException callbackException = catchThrowableOfType(
            () ->
                ccdCaseUpdater.updateCase(
                    exceptionRecord,
                    SERVICE_NAME,
                    true,
                    "idamToken",
                    "userId",
                    EXISTING_CASE_ID,
                    EXISTING_CASE_TYPE_ID
                ),
            CallbackException.class
        );

        // then
        assertThat(callbackException.getMessage())
            .isEqualTo(
                "Failed to update case for " + SERVICE_NAME + " service with case Id "
                    + EXISTING_CASE_ID + " based on exception record "
                    + exceptionRecord.id
            );

        verifyNoInteractions(caseDataUpdater);
    }

    @Test
    void updateCase_should_handle_exception_when_start_event_returns_not_found_response() {
        // given
        given(ccdApi.startEventForCaseWorker(
            any(CcdRequestCredentials.class),
            anyString(),
            anyString(),
            anyString(),
            anyString()
        ))
            .willThrow(new FeignException.NotFound(
                "case not found",
                mock(Request.class),
                "Body".getBytes(),
                null
            ));

        // when
        Optional<ErrorsAndWarnings> res = ccdCaseUpdater.updateCase(
            exceptionRecord,
            SERVICE_NAME,
            true,
            "idamToken",
            "userId",
            "1234123412341234",
            EXISTING_CASE_TYPE_ID
        );

        // then
        assertThat(res).isNotEmpty();
        assertThat(res.get().getErrors())
            .containsOnly("No case found for case ID: 1234123412341234");
        assertThat(res.get().getWarnings()).isEmpty();

        verifyNoInteractions(caseDataUpdater);
    }

    @Test
    void updateCase_should_handle_exception_when_start_event_returns_invalid_case_id_response() {
        // given
        given(ccdApi.startEventForCaseWorker(
            any(CcdRequestCredentials.class),
            anyString(),
            anyString(),
            anyString(),
            anyString()
        ))
            .willThrow(new FeignException.BadRequest(
                "invalid",
                mock(Request.class),
                "Body".getBytes(),
                null
            ));

        // when
        Optional<ErrorsAndWarnings> res = ccdCaseUpdater.updateCase(
            exceptionRecord,
            SERVICE_NAME,
            true,
            "idamToken",
            "userId",
            "1234",
            EXISTING_CASE_TYPE_ID
        );

        // then
        assertThat(res).isNotEmpty();
        assertThat(res.get().getErrors()).containsOnly("Invalid case ID: 1234");
        assertThat(res.get().getWarnings()).isEmpty();

        verifyNoInteractions(caseDataUpdater);
    }

    private BDDMyOngoingStubbing<CaseDetails> prepareMockForSubmissionEventForCaseWorker() {
        return given(ccdApi.updateCaseInCcd(
            anyBoolean(),
            any(CcdRequestCredentials.class),
            any(ExceptionRecord.class),
            any(CaseDetails.class),
            any(CaseDataContent.class)
        ));
    }

    private void initMockData() {
        given(eventResponse.getCaseDetails()).willReturn(existingCase);
        given(ccdApi.startEventForCaseWorker(
            any(CcdRequestCredentials.class),
            anyString(),
            anyString(),
            anyString(),
            anyString()
        ))
            .willReturn(eventResponse);
    }

    private void initResponseMockData() {
        given(existingCase.getCaseTypeId()).willReturn("caseTypeId");
        given(eventResponse.getEventId())
            .willReturn(EventIds.ATTACH_SCANNED_DOCS_WITH_OCR);
        given(eventResponse.getToken()).willReturn("token");
    }

    private ExceptionRecord getExceptionRecord() {
        return new ExceptionRecord(
            "1",
            "caseTypeId",
            "envelopeId123",
            "12345",
            "some jurisdiction",
            SUPPLEMENTARY_EVIDENCE_WITH_OCR,
            "Form1",
            now(),
            now(),
            singletonList(new ScannedDocument(
                DocumentType.FORM,
                "D8",
                new DocumentUrl(
                    "http://locahost",
                    "http://locahost/binary",
                    "file1.pdf"
                ),
                "1234",
                "file1.pdf",
                now(),
                now()
            )),
            emptyList()
        );
    }
}
