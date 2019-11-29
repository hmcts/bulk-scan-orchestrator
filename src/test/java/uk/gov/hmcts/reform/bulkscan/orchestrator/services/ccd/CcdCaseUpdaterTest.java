package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import feign.FeignException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import sun.nio.cs.ext.ISO_8859_11;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGenerator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.InvalidCaseDataException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.caseupdate.CaseUpdateClient;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.caseupdate.model.response.CaseUpdateDetails;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.caseupdate.model.response.SuccessfulUpdateResponse;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.request.ExceptionRecord;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.response.ClientServiceErrorResponse;
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.ServiceConfigItem;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.callback.CallbackException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.callback.ProcessResult;
import uk.gov.hmcts.reform.ccd.client.CoreCaseDataApi;
import uk.gov.hmcts.reform.ccd.client.model.CaseDataContent;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.ccd.client.model.StartEventResponse;

import java.util.HashMap;
import java.util.Map;

import static java.time.LocalDateTime.now;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification.SUPPLEMENTARY_EVIDENCE_WITH_OCR;

@ExtendWith(MockitoExtension.class)
class CcdCaseUpdaterTest {

    private CcdCaseUpdater ccdCaseUpdater;

    @Mock
    private CaseUpdateClient caseUpdateClient;

    @Mock
    private AuthTokenGenerator authTokenGenerator;

    @Mock
    private CoreCaseDataApi coreCaseDataApi;

    @Mock
    private ExceptionRecordFinalizer exceptionRecordFinalizer;

    @Mock
    private ServiceConfigItem configItem;

    @Mock
    private CaseDetails existingCase;

    @Mock
    private StartEventResponse eventResponse;

    @Mock
    private Map<String, Object> originalFields;

    @Mock
    private ClientServiceErrorResponse clientServiceErrorResponse;

    private ExceptionRecord exceptionRecord;

    private SuccessfulUpdateResponse successfulUpdateResponse;

    private CaseUpdateDetails caseUpdateDetails;

    @BeforeEach
    void setUp() {
        ccdCaseUpdater = new CcdCaseUpdater(
            caseUpdateClient,
            authTokenGenerator,
            coreCaseDataApi,
            exceptionRecordFinalizer
        );

        caseUpdateDetails = new CaseUpdateDetails("event_id", new HashMap<String, String>());
        exceptionRecord = getExceptionRecord();

        given(configItem.getService()).willReturn("Service");
        given(existingCase.getId()).willReturn(1L);
        given(configItem.getUpdateUrl()).willReturn("url");
        given(authTokenGenerator.generate()).willReturn("token");
    }

    @Test
    void should_update_case() throws Exception {
        // given
        successfulUpdateResponse = new SuccessfulUpdateResponse(caseUpdateDetails, emptyList());
        given(caseUpdateClient.updateCase("url", existingCase, exceptionRecord, "token"))
            .willReturn(successfulUpdateResponse);
        given(existingCase.getCaseTypeId()).willReturn("caseTypeId");
        given(eventResponse.getEventId()).willReturn("eventId");
        given(eventResponse.getToken()).willReturn("token");
        given(coreCaseDataApi.startForCaseworker(
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString()))
            .willReturn(eventResponse);
        given(coreCaseDataApi.submitForCaseworker(
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyBoolean(),
            any(CaseDataContent.class)
        )).willReturn(CaseDetails.builder().id(1L).build());
        given(exceptionRecordFinalizer.finalizeExceptionRecord(anyMap(), anyLong())).willReturn(originalFields);

        // when
        ProcessResult res = ccdCaseUpdater.updateCase(
            exceptionRecord,
            configItem,
            true,
            "idamToken",
            "userId",
            existingCase
        );

        // then
        assertThat(res.getErrors()).isEmpty();
        assertThat(res.getWarnings()).isEmpty();
        assertThat(res.getExceptionRecordData()).isEqualTo(originalFields);
    }

    @Test
    void should_update_case_ignore_warnings() throws Exception {
        // given
        successfulUpdateResponse = new SuccessfulUpdateResponse(caseUpdateDetails, asList("warning1"));
        given(caseUpdateClient.updateCase("url", existingCase, exceptionRecord, "token"))
            .willReturn(successfulUpdateResponse);
        given(existingCase.getCaseTypeId()).willReturn("caseTypeId");
        given(eventResponse.getEventId()).willReturn("eventId");
        given(eventResponse.getToken()).willReturn("token");
        given(coreCaseDataApi.startForCaseworker(
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString()))
            .willReturn(eventResponse);
        given(coreCaseDataApi.submitForCaseworker(
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyBoolean(),
            any(CaseDataContent.class)
        )).willReturn(CaseDetails.builder().id(1L).build());
        given(exceptionRecordFinalizer.finalizeExceptionRecord(anyMap(), anyLong())).willReturn(originalFields);

        // when
        ProcessResult res = ccdCaseUpdater.updateCase(
            exceptionRecord,
            configItem,
            true,
            "idamToken",
            "userId",
            existingCase
        );

        // then
        assertThat(res.getErrors()).isEmpty();
        assertThat(res.getWarnings()).isEmpty();
        assertThat(res.getExceptionRecordData()).isEqualTo(originalFields);
    }

    @Test
    void should_update_case_not_ignore_warnings() throws Exception {
        // given
        successfulUpdateResponse = new SuccessfulUpdateResponse(caseUpdateDetails, asList("warning1"));
        given(caseUpdateClient.updateCase("url", existingCase, exceptionRecord, "token"))
            .willReturn(successfulUpdateResponse);

        // when
        ProcessResult res = ccdCaseUpdater.updateCase(
            exceptionRecord,
            configItem,
            false,
            "idamToken",
            "userId",
            existingCase
        );

        // then
        assertThat(res.getErrors()).isEmpty();
        assertThat(res.getWarnings()).containsOnly("warning1");
        assertThat(res.getExceptionRecordData()).isEmpty();
    }

    @Test
    void should_update_case_pass_if_no_warnings() throws Exception {
        // given
        successfulUpdateResponse = new SuccessfulUpdateResponse(caseUpdateDetails, emptyList());
        given(caseUpdateClient.updateCase("url", existingCase, exceptionRecord, "token"))
            .willReturn(successfulUpdateResponse);
        given(existingCase.getCaseTypeId()).willReturn("caseTypeId");
        given(eventResponse.getEventId()).willReturn("eventId");
        given(eventResponse.getToken()).willReturn("token");
        given(coreCaseDataApi.startForCaseworker(
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString()))
            .willReturn(eventResponse);
        given(coreCaseDataApi.submitForCaseworker(
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyBoolean(),
            any(CaseDataContent.class)
        )).willReturn(CaseDetails.builder().id(1L).build());
        given(exceptionRecordFinalizer.finalizeExceptionRecord(anyMap(), anyLong())).willReturn(originalFields);

        // when
        ProcessResult res = ccdCaseUpdater.updateCase(
            exceptionRecord,
            configItem,
            false,
            "idamToken",
            "userId",
            existingCase
        );

        // then
        assertThat(res.getErrors()).isEmpty();
        assertThat(res.getWarnings()).isEmpty();
        assertThat(res.getExceptionRecordData()).isEqualTo(originalFields);
    }

    @Test
    void should_update_case_handle_feign_exception() throws Exception {
        // given
        successfulUpdateResponse = new SuccessfulUpdateResponse(caseUpdateDetails, emptyList());
        given(caseUpdateClient.updateCase("url", existingCase, exceptionRecord, "token"))
            .willReturn(successfulUpdateResponse);
        given(existingCase.getCaseTypeId()).willReturn("caseTypeId");
        given(eventResponse.getEventId()).willReturn("eventId");
        given(eventResponse.getToken()).willReturn("token");
        given(coreCaseDataApi.startForCaseworker(
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString()))
            .willReturn(eventResponse);
        given(coreCaseDataApi.submitForCaseworker(
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyBoolean(),
            any(CaseDataContent.class)
        )).willThrow(new FeignException.BadRequest("Msg", "Body".getBytes()));

        // when
        CallbackException callbackException = catchThrowableOfType(() ->
                ccdCaseUpdater.updateCase(
                    exceptionRecord,
                    configItem,
                    true,
                    "idamToken",
                    "userId",
                    existingCase
                ),
            CallbackException.class
        );

        // then
        assertThat(callbackException.getMessage()).isEqualTo("Failed to update case");
    }

    @Test
    void should_update_case_handle_invalid_case_data_exception() throws Exception {
        // given
        successfulUpdateResponse = new SuccessfulUpdateResponse(caseUpdateDetails, emptyList());
        given(caseUpdateClient.updateCase("url", existingCase, exceptionRecord, "token"))
            .willReturn(successfulUpdateResponse);
        given(existingCase.getCaseTypeId()).willReturn("caseTypeId");
        given(eventResponse.getEventId()).willReturn("eventId");
        given(eventResponse.getToken()).willReturn("token");
        given(coreCaseDataApi.startForCaseworker(
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString()))
            .willReturn(eventResponse);
        given(coreCaseDataApi.submitForCaseworker(
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyBoolean(),
            any(CaseDataContent.class)
        )).willThrow(new InvalidCaseDataException(
            HttpClientErrorException.create(
                HttpStatus.BAD_REQUEST,
                "",
                HttpHeaders.EMPTY,
                "".getBytes(),
                new ISO_8859_11()
            ),
            clientServiceErrorResponse));

        // when
        CallbackException callbackException = catchThrowableOfType(() ->
                ccdCaseUpdater.updateCase(
                    exceptionRecord,
                    configItem,
                    true,
                    "idamToken",
                    "userId",
                    existingCase
                ),
            CallbackException.class
        );

        // then
        assertThat(callbackException.getMessage()).isEqualTo("Failed to update case");
    }

    @Test
    void should_update_case_handle_exception() throws Exception {
        // given
        successfulUpdateResponse = new SuccessfulUpdateResponse(caseUpdateDetails, emptyList());
        given(caseUpdateClient.updateCase("url", existingCase, exceptionRecord, "token"))
            .willReturn(successfulUpdateResponse);
        given(existingCase.getCaseTypeId()).willReturn("caseTypeId");
        given(eventResponse.getEventId()).willReturn("eventId");
        given(eventResponse.getToken()).willReturn("token");
        given(coreCaseDataApi.startForCaseworker(
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString()))
            .willReturn(eventResponse);
        given(coreCaseDataApi.submitForCaseworker(
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyBoolean(),
            any(CaseDataContent.class)
        )).willThrow(new RuntimeException("Msg"));

        // when
        CallbackException callbackException = catchThrowableOfType(() ->
                ccdCaseUpdater.updateCase(
                    exceptionRecord,
                    configItem,
                    true,
                    "idamToken",
                    "userId",
                    existingCase
                ),
            CallbackException.class
        );

        // then
        assertThat(callbackException.getMessage()).isEqualTo("Failed to update case");
    }

    private ExceptionRecord getExceptionRecord() {
        return new ExceptionRecord(
            "1",
            "caseTypeId",
            "12345",
            "some jurisdiction",
            SUPPLEMENTARY_EVIDENCE_WITH_OCR,
            "Form1",
            now(),
            now(),
            emptyList(),
            emptyList()
        );
    }
}
