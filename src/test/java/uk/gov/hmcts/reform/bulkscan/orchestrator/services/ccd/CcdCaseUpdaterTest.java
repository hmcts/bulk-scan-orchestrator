package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import feign.FeignException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.BDDMockito.BDDMyOngoingStubbing;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGenerator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.ServiceResponseParser;
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
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.EventIdValidator.EVENT_ID_UPDATE_CASE;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification.SUPPLEMENTARY_EVIDENCE_WITH_OCR;

@ExtendWith(MockitoExtension.class)
class CcdCaseUpdaterTest {

    private static final String EXISTING_CASE_ID = "existing_case_id";

    private CcdCaseUpdater ccdCaseUpdater;

    @Mock
    private CaseUpdateClient caseUpdateClient;

    @Mock
    private ServiceResponseParser serviceResponseParser;

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

    private SuccessfulUpdateResponse noWarningsUpdateResponse;
    private SuccessfulUpdateResponse warningsUpdateResponse;

    private CaseUpdateDetails caseUpdateDetails;

    @BeforeEach
    void setUp() {
        ccdCaseUpdater = new CcdCaseUpdater(
            authTokenGenerator,
            coreCaseDataApi,
            caseUpdateClient,
            serviceResponseParser,
            exceptionRecordFinalizer
        );

        caseUpdateDetails = new CaseUpdateDetails("event_id", new HashMap<String, String>());
        exceptionRecord = getExceptionRecord();

        noWarningsUpdateResponse = new SuccessfulUpdateResponse(caseUpdateDetails, emptyList());
        warningsUpdateResponse = new SuccessfulUpdateResponse(caseUpdateDetails, asList("warning1"));

        given(configItem.getService()).willReturn("Service");
        given(configItem.getUpdateUrl()).willReturn("url");
        given(authTokenGenerator.generate()).willReturn("token");
    }

    @Test
    void updateCase_should_if_no_warnings() throws Exception {
        // given
        given(caseUpdateClient.updateCase("url", existingCase, exceptionRecord, "token"))
            .willReturn(noWarningsUpdateResponse);
        initResponseMockData();
        initMockData();
        prepareMockForSubmissionEventForCaseWorker().willReturn(CaseDetails.builder().id(1L).build());
        given(exceptionRecordFinalizer.finalizeExceptionRecord(anyMap(), anyLong())).willReturn(originalFields);

        // when
        ProcessResult res = ccdCaseUpdater.updateCase(
            exceptionRecord,
            configItem,
            true,
            "idamToken",
            "userId",
            EXISTING_CASE_ID,
            EVENT_ID_UPDATE_CASE
        );

        // then
        assertThat(res.getErrors()).isEmpty();
        assertThat(res.getWarnings()).isEmpty();
        assertThat(res.getExceptionRecordData()).isEqualTo(originalFields);
    }

    @Test
    void updateCase_should_ignore_warnings() throws Exception {
        // given
        given(caseUpdateClient.updateCase("url", existingCase, exceptionRecord, "token"))
            .willReturn(noWarningsUpdateResponse);
        initResponseMockData();
        initMockData();
        prepareMockForSubmissionEventForCaseWorker().willReturn(CaseDetails.builder().id(1L).build());
        given(exceptionRecordFinalizer.finalizeExceptionRecord(anyMap(), anyLong())).willReturn(originalFields);

        // when
        ProcessResult res = ccdCaseUpdater.updateCase(
            exceptionRecord,
            configItem,
            true,
            "idamToken",
            "userId",
            EXISTING_CASE_ID,
            EVENT_ID_UPDATE_CASE
        );

        // then
        assertThat(res.getErrors()).isEmpty();
        assertThat(res.getWarnings()).isEmpty();
        assertThat(res.getExceptionRecordData()).isEqualTo(originalFields);
    }

    @Test
    void updateCase_should_not_ignore_warnings() throws Exception {
        // given
        given(caseUpdateClient.updateCase("url", existingCase, exceptionRecord, "token"))
            .willReturn(warningsUpdateResponse);
        initMockData();

        // when
        ProcessResult res = ccdCaseUpdater.updateCase(
            exceptionRecord,
            configItem,
            false,
            "idamToken",
            "userId",
            EXISTING_CASE_ID,
            EVENT_ID_UPDATE_CASE
        );

        // then
        assertThat(res.getErrors()).isEmpty();
        assertThat(res.getWarnings()).containsOnly("warning1");
        assertThat(res.getExceptionRecordData()).isEmpty();
    }

    @Test
    void updateCase_should_pass_if_no_warnings() throws Exception {
        // given
        given(caseUpdateClient.updateCase("url", existingCase, exceptionRecord, "token"))
            .willReturn(noWarningsUpdateResponse);
        initResponseMockData();
        initMockData();
        prepareMockForSubmissionEventForCaseWorker().willReturn(CaseDetails.builder().id(1L).build());
        given(exceptionRecordFinalizer.finalizeExceptionRecord(anyMap(), anyLong())).willReturn(originalFields);

        // when
        ProcessResult res = ccdCaseUpdater.updateCase(
            exceptionRecord,
            configItem,
            false,
            "idamToken",
            "userId",
            EXISTING_CASE_ID,
            EVENT_ID_UPDATE_CASE
        );

        // then
        assertThat(res.getErrors()).isEmpty();
        assertThat(res.getWarnings()).isEmpty();
        assertThat(res.getExceptionRecordData()).isEqualTo(originalFields);
    }

    @Test
    void updateCase_should_handle_feign_exception() throws Exception {
        // given
        noWarningsUpdateResponse = new SuccessfulUpdateResponse(caseUpdateDetails, emptyList());
        given(caseUpdateClient.updateCase("url", existingCase, exceptionRecord, "token"))
            .willReturn(noWarningsUpdateResponse);
        initMockData();
        prepareMockForSubmissionEventForCaseWorker().willThrow(new FeignException.BadRequest("Msg", "Body".getBytes()));

        // when
        CallbackException callbackException = catchThrowableOfType(() ->
                ccdCaseUpdater.updateCase(
                    exceptionRecord,
                    configItem,
                    true,
                    "idamToken",
                    "userId",
                    EXISTING_CASE_ID,
                    EVENT_ID_UPDATE_CASE
                ),
            CallbackException.class
        );

        // then
        assertThat(callbackException.getMessage())
            .isEqualTo("Failed to update case for Service service with case Id existing_case_id "
                + "based on exception record 1");
    }

    @Test
    void updateCase_should_handle_exception() throws Exception {
        // given
        noWarningsUpdateResponse = new SuccessfulUpdateResponse(caseUpdateDetails, emptyList());

        // when
        CallbackException callbackException = catchThrowableOfType(() ->
                ccdCaseUpdater.updateCase(
                    exceptionRecord,
                    configItem,
                    true,
                    "idamToken",
                    "userId",
                    EXISTING_CASE_ID,
                    EVENT_ID_UPDATE_CASE
                ),
            CallbackException.class
        );

        // then
        assertThat(callbackException.getMessage())
            .isEqualTo("Failed to update case for Service service with case Id existing_case_id "
                + "based on exception record 1");
    }

    private BDDMyOngoingStubbing<CaseDetails> prepareMockForSubmissionEventForCaseWorker() {
        return given(coreCaseDataApi.submitForCaseworker(
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
            anyString()))
            .willReturn(eventResponse);
    }

    private void initResponseMockData() {
        given(existingCase.getCaseTypeId()).willReturn("caseTypeId");
        given(eventResponse.getEventId()).willReturn(EVENT_ID_UPDATE_CASE);
        given(eventResponse.getToken()).willReturn("token");
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
