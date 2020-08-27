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
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.caseupdate.CaseUpdateClient;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.caseupdate.model.response.CaseUpdateDetails;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.caseupdate.model.response.SuccessfulUpdateResponse;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.request.DocumentType;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.request.DocumentUrl;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.request.ScannedDocument;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.response.ClientServiceErrorResponse;
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.ServiceConfigItem;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.internal.ExceptionRecord;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.callback.CallbackException;
import uk.gov.hmcts.reform.ccd.client.CoreCaseDataApi;
import uk.gov.hmcts.reform.ccd.client.model.CaseDataContent;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.ccd.client.model.StartEventResponse;

import java.util.HashMap;
import java.util.Optional;
import javax.validation.ConstraintViolationException;

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
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification.SUPPLEMENTARY_EVIDENCE_WITH_OCR;

@ExtendWith(MockitoExtension.class)
class CcdCaseUpdaterTest {

    private static final String EXISTING_CASE_ID = "existing_case_id";
    private static final String EXISTING_CASE_TYPE_ID = "existing_case_type";

    private CcdCaseUpdater ccdCaseUpdater;

    @Mock private CaseUpdateClient caseUpdateClient;
    @Mock private ServiceResponseParser serviceResponseParser;
    @Mock private AuthTokenGenerator authTokenGenerator;
    @Mock private CoreCaseDataApi coreCaseDataApi;
    @Mock private ServiceConfigItem configItem;
    @Mock private CaseDetails existingCase;
    @Mock private StartEventResponse eventResponse;

    private ExceptionRecord exceptionRecord;

    private SuccessfulUpdateResponse noWarningsUpdateResponse;
    private SuccessfulUpdateResponse warningsUpdateResponse;

    @BeforeEach
    void setUp() {
        ccdCaseUpdater = new CcdCaseUpdater(
            authTokenGenerator,
            coreCaseDataApi,
            caseUpdateClient,
            serviceResponseParser
        );

        CaseUpdateDetails caseUpdateDetails = new CaseUpdateDetails(null, new HashMap<String, String>());
        exceptionRecord = getExceptionRecord();

        noWarningsUpdateResponse = new SuccessfulUpdateResponse(caseUpdateDetails, emptyList());
        warningsUpdateResponse = new SuccessfulUpdateResponse(caseUpdateDetails, singletonList("warning1"));

        given(configItem.getService()).willReturn("Service");
        given(authTokenGenerator.generate()).willReturn("token");
    }

    @Test
    void updateCase_should_pass_if_no_warnings() {
        // given
        given(configItem.getUpdateUrl()).willReturn("url");
        given(caseUpdateClient.updateCase("url", existingCase, exceptionRecord, "token"))
            .willReturn(noWarningsUpdateResponse);
        initResponseMockData();
        initMockData();
        prepareMockForSubmissionEventForCaseWorker().willReturn(CaseDetails.builder().id(1L).build());

        // when
        Optional<ErrorsAndWarnings> res = ccdCaseUpdater.updateCase(
            exceptionRecord,
            configItem,
            false,
            "idamToken",
            "userId",
            EXISTING_CASE_ID,
            EXISTING_CASE_TYPE_ID
        );

        // then
        assertThat(res).isEmpty();
    }

    @Test
    void updateCase_should_ignore_warnings_if_ignoreWarnings_is_true() {
        // given
        given(configItem.getUpdateUrl()).willReturn("url");
        given(caseUpdateClient.updateCase("url", existingCase, exceptionRecord, "token"))
            .willReturn(warningsUpdateResponse);
        initResponseMockData();
        initMockData();
        prepareMockForSubmissionEventForCaseWorker().willReturn(CaseDetails.builder().id(1L).build());

        // when
        Optional<ErrorsAndWarnings> res = ccdCaseUpdater.updateCase(
            exceptionRecord,
            configItem,
            true,
            "idamToken",
            "userId",
            EXISTING_CASE_ID,
            EXISTING_CASE_TYPE_ID
        );

        // then
        assertThat(res).isEmpty();
    }

    @Test
    void updateCase_should_not_ignore_warnings_if_ignoreWarnings_is_false() {
        // given
        given(configItem.getUpdateUrl()).willReturn("url");
        given(caseUpdateClient.updateCase("url", existingCase, exceptionRecord, "token"))
            .willReturn(warningsUpdateResponse);
        initMockData();

        // when
        Optional<ErrorsAndWarnings> res = ccdCaseUpdater.updateCase(
            exceptionRecord,
            configItem,
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
    }

    @Test
    void updateCase_should_handle_conflict_response_from_ccd_api() {
        initResponseMockData();
        given(configItem.getUpdateUrl()).willReturn("url");
        given(caseUpdateClient.updateCase(anyString(), any(CaseDetails.class), any(ExceptionRecord.class), anyString()))
            .willReturn(noWarningsUpdateResponse);
        initMockData();
        prepareMockForSubmissionEventForCaseWorker()
            .willThrow(new FeignException.Conflict("Msg", mock(Request.class), "Body".getBytes()));

        // when
        Optional<ErrorsAndWarnings> res = ccdCaseUpdater.updateCase(
            exceptionRecord,
            configItem,
            true,
            "idamToken",
            "userId",
            EXISTING_CASE_ID,
            EXISTING_CASE_TYPE_ID
        );

        // then
        assertThat(res).isNotEmpty();
        assertThat(res.get().getErrors()).containsExactlyInAnyOrder("Failed to update case for Service service "
            + "with case Id existing_case_id based on exception record 1 because it has been updated in the meantime");
        assertThat(res.get().getWarnings()).isEmpty();
    }

    @Test
    void updateCase_should_handle_feign_exception() {
        // given
        initResponseMockData();
        given(configItem.getUpdateUrl()).willReturn("url");
        given(caseUpdateClient.updateCase(anyString(), any(CaseDetails.class), any(ExceptionRecord.class), anyString()))
            .willReturn(noWarningsUpdateResponse);
        initMockData();
        prepareMockForSubmissionEventForCaseWorker().willThrow(
                new FeignException.BadRequest("Msg", mock(Request.class), "Body".getBytes())
        );

        // when
        CallbackException callbackException = catchThrowableOfType(() ->
                ccdCaseUpdater.updateCase(
                    exceptionRecord,
                    configItem,
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
            .isEqualTo("Failed to update case for Service service with case Id existing_case_id "
                + "based on exception record 1");
        assertThat(callbackException.getCause().getMessage()).isEqualTo("Service response: Body");
    }

    @Test
    void updateCase_should_handle_feign_unprocessable_entity() {
        // given
        initResponseMockData();
        given(configItem.getUpdateUrl()).willReturn("url");
        given(caseUpdateClient.updateCase(anyString(), any(CaseDetails.class), any(ExceptionRecord.class), anyString()))
            .willReturn(noWarningsUpdateResponse);
        initMockData();
        prepareMockForSubmissionEventForCaseWorker()
            .willThrow(
                    new FeignException.UnprocessableEntity("Msg", mock(Request.class),  "Body".getBytes())
            );

        // when
        CallbackException exception = catchThrowableOfType(() ->
                ccdCaseUpdater.updateCase(
                    exceptionRecord,
                    configItem,
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
                configItem.getService(),
                EXISTING_CASE_ID,
                exceptionRecord.id
            );
    }

    @Test
    void updateCase_should_handle_rest_template_client_exception_for_i_o_exceptions() {
        // given
        given(existingCase.getCaseTypeId()).willReturn("caseTypeId");
        given(eventResponse.getEventId()).willReturn(EventIds.ATTACH_SCANNED_DOCS_WITH_OCR);
        given(configItem.getUpdateUrl()).willReturn("url");
        given(caseUpdateClient.updateCase(anyString(), any(CaseDetails.class), any(ExceptionRecord.class), anyString()))
            .willThrow(new RestClientException("I/O error"));
        initMockData();

        // when
        CallbackException exception = catchThrowableOfType(() ->
                ccdCaseUpdater.updateCase(
                    exceptionRecord,
                    configItem,
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
                configItem.getService(),
                EXISTING_CASE_ID,
                exceptionRecord.id
            );
    }

    @Test
    void updateCase_should_handle_response_validation_exception() {
        // given
        given(existingCase.getCaseTypeId()).willReturn("caseTypeId");
        given(eventResponse.getEventId()).willReturn(EventIds.ATTACH_SCANNED_DOCS_WITH_OCR);
        given(configItem.getUpdateUrl()).willReturn("url");
        given(caseUpdateClient.updateCase(anyString(), any(CaseDetails.class), any(ExceptionRecord.class), anyString()))
            .willThrow(new ConstraintViolationException("validation error message", emptySet()));
        initMockData();

        // when
        Throwable exception = catchThrowable(() ->
                ccdCaseUpdater.updateCase(
                    exceptionRecord,
                    configItem,
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
    }

    @Test
    void updateCase_should_handle_bad_request_from_start_event() {
        // given
        given(coreCaseDataApi.startEventForCaseWorker(
            anyString(),
            anyString(),
            anyString(),
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
        CallbackException callbackException = catchThrowableOfType(() ->
                ccdCaseUpdater.updateCase(
                    exceptionRecord,
                    configItem,
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
            .isEqualTo("Failed to update case for Service service with case Id existing_case_id "
                + "based on exception record 1");
        assertThat(callbackException.getCause().getMessage()).isEqualTo("400 bad request message");
        assertThat(callbackException.getCause() instanceof HttpClientErrorException).isTrue();
        assertThat(((HttpClientErrorException) callbackException.getCause()).getStatusText())
            .isEqualTo("bad request message");
        assertThat(((HttpClientErrorException) callbackException.getCause()).getRawStatusCode()).isEqualTo(400);
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
        given(coreCaseDataApi.startEventForCaseWorker(
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString()
        ))
            .willThrow(unprocessableEntity);
        given(serviceResponseParser.parseResponseBody(unprocessableEntity))
            .willReturn(new ClientServiceErrorResponse(asList("error1", "error2"), emptyList()));

        // when
        Optional<ErrorsAndWarnings> res = ccdCaseUpdater.updateCase(
            exceptionRecord,
            configItem,
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
    }

    @Test
    void updateCase_should_handle_feign_exception_from_start_event() {
        // given
        given(coreCaseDataApi.startEventForCaseWorker(
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString()
        ))
            .willThrow(new FeignException.MethodNotAllowed("Msg", mock(Request.class),  "Body".getBytes()));

        // when
        CallbackException callbackException = catchThrowableOfType(() ->
                ccdCaseUpdater.updateCase(
                    exceptionRecord,
                    configItem,
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
            .isEqualTo("Failed to update case for Service service with case Id existing_case_id "
                + "based on exception record 1. Service response: Body");
        assertThat(callbackException.getCause().getMessage()).isEqualTo("Msg");
    }

    @Test
    void updateCase_should_handle_exception() {
        // given
        given(coreCaseDataApi.startEventForCaseWorker(
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString()
        ))
            .willThrow(new RuntimeException());

        // when
        CallbackException callbackException = catchThrowableOfType(() ->
                ccdCaseUpdater.updateCase(
                    exceptionRecord,
                    configItem,
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
            .isEqualTo("Failed to update case for Service service with case Id existing_case_id "
                + "based on exception record 1");
    }

    @Test
    void updateCase_should_handle_exception_when_start_event_returns_not_found_response() {
        // given
        given(coreCaseDataApi.startEventForCaseWorker(
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString()
        ))
            .willThrow(
                    new FeignException.NotFound("case not found",  mock(Request.class), "Body".getBytes())
            );

        // when
        Optional<ErrorsAndWarnings> res = ccdCaseUpdater.updateCase(
            exceptionRecord,
            configItem,
            true,
            "idamToken",
            "userId",
            "1234123412341234",
            EXISTING_CASE_TYPE_ID
        );

        // then
        assertThat(res).isNotEmpty();
        assertThat(res.get().getErrors()).containsOnly("No case found for case ID: 1234123412341234");
        assertThat(res.get().getWarnings()).isEmpty();
    }

    @Test
    void updateCase_should_handle_exception_when_start_event_returns_invalid_case_id_response() {
        // given
        given(coreCaseDataApi.startEventForCaseWorker(
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString()
        ))
            .willThrow(new FeignException.BadRequest("invalid", mock(Request.class),  "Body".getBytes()));

        // when
        Optional<ErrorsAndWarnings> res = ccdCaseUpdater.updateCase(
            exceptionRecord,
            configItem,
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
    }

    private BDDMyOngoingStubbing<CaseDetails> prepareMockForSubmissionEventForCaseWorker() {
        return given(coreCaseDataApi.submitEventForCaseWorker(
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyBoolean(),
            any(CaseDataContent.class)
        ));
    }

    private void initMockData() {
        given(eventResponse.getCaseDetails()).willReturn(existingCase);
        given(coreCaseDataApi.startEventForCaseWorker(
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString()
        ))
            .willReturn(eventResponse);
    }

    private void initResponseMockData() {
        given(existingCase.getCaseTypeId()).willReturn("caseTypeId");
        given(eventResponse.getEventId()).willReturn(EventIds.ATTACH_SCANNED_DOCS_WITH_OCR);
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
