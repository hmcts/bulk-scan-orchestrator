package uk.gov.hmcts.reform.bulkscan.orchestrator.services;

import feign.FeignException;
import feign.Request;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.BDDMockito;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.caseupdate.model.response.CaseUpdateDetails;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.request.ExceptionRecord;
import uk.gov.hmcts.reform.ccd.client.CoreCaseDataApi;
import uk.gov.hmcts.reform.ccd.client.model.CaseDataContent;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.ccd.client.model.StartEventResponse;

import java.util.Optional;

import static java.time.LocalDateTime.now;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification.NEW_APPLICATION;

@ExtendWith(MockitoExtension.class)
class CcdCaseUpdateFinalizerTest {
    private static final String SERVICE = "service";
    private static final boolean IGNORE_WARNINGS = true;
    private static final String IDAM_TOKEN = "idamToken";
    private static final String S2S_TOKEN = "s2sToken";
    private static final String USER_ID = "userId";
    private static final long EXISTING_CASE_ID = 110L;
    private static final String EXISTING_CASE_ID_STR = String.valueOf(EXISTING_CASE_ID);
    private static final String PO_BOX_JURISDICTION = "poBoxJurisdiction";
    private static final String EXCEPTION_RECORD_ID = "id";
    private static final String CASE_TYPE_ID = "caseTypeId";
    private static final String FORM_TYPE = "formType";
    private static final String PO_BOX = "poBox";
    private static final String EVENT_ID = "eventId";
    private static final Object CASE_DATA = new Object();
    private static final String EVENT_TOKEN = "token";
    private static final String BODY = "body";
    private static final String MSG = "msg";

    @Mock
    private CoreCaseDataApi coreCaseDataApi;

    private ExceptionRecord exceptionRecord;

    private CaseUpdateDetails caseUpdateDetails;

    @Mock
    private StartEventResponse startEvent;

    @Mock
    private CaseDetails caseDetails;

    private CcdCaseUpdateFinalizer ccdCaseUpdateFinalizer;

    @BeforeEach
    void setUp() {
        ccdCaseUpdateFinalizer = new CcdCaseUpdateFinalizer(coreCaseDataApi);

        exceptionRecord = new ExceptionRecord(
            EXCEPTION_RECORD_ID,
            CASE_TYPE_ID,
            PO_BOX,
            PO_BOX_JURISDICTION,
            NEW_APPLICATION,
            FORM_TYPE,
            now(),
            now(),
            emptyList(),
            emptyList()
        );

        caseUpdateDetails = new CaseUpdateDetails(EVENT_ID, CASE_DATA);

        given(startEvent.getEventId()).willReturn(EVENT_ID);
        given(startEvent.getToken()).willReturn(EVENT_TOKEN);
        given(startEvent.getCaseDetails()).willReturn(caseDetails);
        given(caseDetails.getCaseTypeId()).willReturn(CASE_TYPE_ID);
        given(caseDetails.getId()).willReturn(EXISTING_CASE_ID);
    }

    @Test
    void updateCaseInCcd_should_return_no_errors() {
        // given


        // when
        Optional<String> res = ccdCaseUpdateFinalizer.updateCaseInCcd(
            SERVICE,
            IGNORE_WARNINGS,
            IDAM_TOKEN,
            S2S_TOKEN,
            USER_ID,
            EXISTING_CASE_ID_STR,
            exceptionRecord,
            caseUpdateDetails,
            startEvent
        );

        // then
        ArgumentCaptor<String> idamTokenCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> s2sTokenCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> userIdCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> poBoxJurisdictionCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> caseTypeIdCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> existingCaseIdCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Boolean> ignoreWarningsCaptor = ArgumentCaptor.forClass(Boolean.class);
        ArgumentCaptor<CaseDataContent> caseDataContentCaptor =
            ArgumentCaptor.forClass(CaseDataContent.class);
        verify(coreCaseDataApi).submitEventForCaseWorker(
            idamTokenCaptor.capture(),
            s2sTokenCaptor.capture(),
            userIdCaptor.capture(),
            poBoxJurisdictionCaptor.capture(),
            caseTypeIdCaptor.capture(),
            existingCaseIdCaptor.capture(),
            ignoreWarningsCaptor.capture(),
            caseDataContentCaptor.capture()
        );
        assertThat(idamTokenCaptor.getValue()).isEqualTo(IDAM_TOKEN);
        assertThat(s2sTokenCaptor.getValue()).isEqualTo(S2S_TOKEN);
        assertThat(userIdCaptor.getValue()).isEqualTo(USER_ID);
        assertThat(poBoxJurisdictionCaptor.getValue()).isEqualTo(PO_BOX_JURISDICTION);
        assertThat(caseTypeIdCaptor.getValue()).isEqualTo(CASE_TYPE_ID);
        assertThat(existingCaseIdCaptor.getValue()).isEqualTo(EXISTING_CASE_ID_STR);
        assertThat(ignoreWarningsCaptor.getValue()).isEqualTo(IGNORE_WARNINGS);
        assertThat(caseDataContentCaptor.getValue().getCaseReference()).isEqualTo(EXCEPTION_RECORD_ID);
        assertThat(caseDataContentCaptor.getValue().getData()).isEqualTo(CASE_DATA);
        assertThat(caseDataContentCaptor.getValue().getEventToken()).isEqualTo(EVENT_TOKEN);
        assertThat(res).isEmpty();
    }

    @Test
    void updateCaseInCcd_should_return_error_if_unprocessable_entity() {
        // given
        prepareEventSubmissionForCaseWorker()
            .willThrow(
                new FeignException.UnprocessableEntity(
                    "msg",
                    mock(Request.class),
                    BODY.getBytes())
            );

        // when
        Optional<String> res = ccdCaseUpdateFinalizer.updateCaseInCcd(
            SERVICE,
            IGNORE_WARNINGS,
            IDAM_TOKEN,
            S2S_TOKEN,
            USER_ID,
            EXISTING_CASE_ID_STR,
            exceptionRecord,
            caseUpdateDetails,
            startEvent
        );

        // then
        ArgumentCaptor<String> idamTokenCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> s2sTokenCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> userIdCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> poBoxJurisdictionCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> caseTypeIdCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> existingCaseIdCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Boolean> ignoreWarningsCaptor = ArgumentCaptor.forClass(Boolean.class);
        ArgumentCaptor<CaseDataContent> caseDataContentCaptor =
            ArgumentCaptor.forClass(CaseDataContent.class);
        verify(coreCaseDataApi).submitEventForCaseWorker(
            idamTokenCaptor.capture(),
            s2sTokenCaptor.capture(),
            userIdCaptor.capture(),
            poBoxJurisdictionCaptor.capture(),
            caseTypeIdCaptor.capture(),
            existingCaseIdCaptor.capture(),
            ignoreWarningsCaptor.capture(),
            caseDataContentCaptor.capture()
        );
        assertThat(idamTokenCaptor.getValue()).isEqualTo(IDAM_TOKEN);
        assertThat(s2sTokenCaptor.getValue()).isEqualTo(S2S_TOKEN);
        assertThat(userIdCaptor.getValue()).isEqualTo(USER_ID);
        assertThat(poBoxJurisdictionCaptor.getValue()).isEqualTo(PO_BOX_JURISDICTION);
        assertThat(caseTypeIdCaptor.getValue()).isEqualTo(CASE_TYPE_ID);
        assertThat(existingCaseIdCaptor.getValue()).isEqualTo(EXISTING_CASE_ID_STR);
        assertThat(ignoreWarningsCaptor.getValue()).isEqualTo(IGNORE_WARNINGS);
        assertThat(caseDataContentCaptor.getValue().getCaseReference()).isEqualTo(EXCEPTION_RECORD_ID);
        assertThat(caseDataContentCaptor.getValue().getData()).isEqualTo(CASE_DATA);
        assertThat(caseDataContentCaptor.getValue().getEventToken()).isEqualTo(EVENT_TOKEN);
        assertThat(res).isNotEmpty();
        assertThat(res.get()).isEqualTo(
            "CCD returned 422 Unprocessable Entity response when trying to update case for "
                + PO_BOX_JURISDICTION
                + " jurisdiction with case Id "
                + EXISTING_CASE_ID
                + " based on exception record with Id "
                + EXCEPTION_RECORD_ID
                + ". CCD response: "
                + BODY
        );
    }

    @Test
    void updateCaseInCcd_should_throw_if_conflict() {
        // given
        prepareEventSubmissionForCaseWorker()
            .willThrow(
                new FeignException.Conflict(
                    MSG,
                    mock(Request.class),
                    BODY.getBytes())
            );

        // when
        FeignException.Conflict ex = catchThrowableOfType(
            () -> ccdCaseUpdateFinalizer.updateCaseInCcd(
                SERVICE,
                IGNORE_WARNINGS,
                IDAM_TOKEN,
                S2S_TOKEN,
                USER_ID,
                EXISTING_CASE_ID_STR,
                exceptionRecord,
                caseUpdateDetails,
                startEvent
            ),
            FeignException.Conflict.class
        );

        // then
        ArgumentCaptor<String> idamTokenCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> s2sTokenCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> userIdCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> poBoxJurisdictionCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> caseTypeIdCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> existingCaseIdCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Boolean> ignoreWarningsCaptor = ArgumentCaptor.forClass(Boolean.class);
        ArgumentCaptor<CaseDataContent> caseDataContentCaptor =
            ArgumentCaptor.forClass(CaseDataContent.class);
        verify(coreCaseDataApi).submitEventForCaseWorker(
            idamTokenCaptor.capture(),
            s2sTokenCaptor.capture(),
            userIdCaptor.capture(),
            poBoxJurisdictionCaptor.capture(),
            caseTypeIdCaptor.capture(),
            existingCaseIdCaptor.capture(),
            ignoreWarningsCaptor.capture(),
            caseDataContentCaptor.capture()
        );
        assertThat(idamTokenCaptor.getValue()).isEqualTo(IDAM_TOKEN);
        assertThat(s2sTokenCaptor.getValue()).isEqualTo(S2S_TOKEN);
        assertThat(userIdCaptor.getValue()).isEqualTo(USER_ID);
        assertThat(poBoxJurisdictionCaptor.getValue()).isEqualTo(PO_BOX_JURISDICTION);
        assertThat(caseTypeIdCaptor.getValue()).isEqualTo(CASE_TYPE_ID);
        assertThat(existingCaseIdCaptor.getValue()).isEqualTo(EXISTING_CASE_ID_STR);
        assertThat(ignoreWarningsCaptor.getValue()).isEqualTo(IGNORE_WARNINGS);
        assertThat(caseDataContentCaptor.getValue().getCaseReference()).isEqualTo(EXCEPTION_RECORD_ID);
        assertThat(caseDataContentCaptor.getValue().getData()).isEqualTo(CASE_DATA);
        assertThat(caseDataContentCaptor.getValue().getEventToken()).isEqualTo(EVENT_TOKEN);
        assertThat(ex.getMessage()).isEqualTo(MSG);
    }

    @Test
    void updateCaseInCcd_should_throw_if_feign_exception() {
        // given
        prepareEventSubmissionForCaseWorker()
            .willThrow(
                new FeignException.BadRequest(
                    MSG,
                    mock(Request.class),
                    BODY.getBytes())
            );

        // when
        RuntimeException ex = catchThrowableOfType(
            () -> ccdCaseUpdateFinalizer.updateCaseInCcd(
                SERVICE,
                IGNORE_WARNINGS,
                IDAM_TOKEN,
                S2S_TOKEN,
                USER_ID,
                EXISTING_CASE_ID_STR,
                exceptionRecord,
                caseUpdateDetails,
                startEvent
            ),
            RuntimeException.class
        );

        // then
        ArgumentCaptor<String> idamTokenCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> s2sTokenCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> userIdCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> poBoxJurisdictionCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> caseTypeIdCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> existingCaseIdCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Boolean> ignoreWarningsCaptor = ArgumentCaptor.forClass(Boolean.class);
        ArgumentCaptor<CaseDataContent> caseDataContentCaptor =
            ArgumentCaptor.forClass(CaseDataContent.class);
        verify(coreCaseDataApi).submitEventForCaseWorker(
            idamTokenCaptor.capture(),
            s2sTokenCaptor.capture(),
            userIdCaptor.capture(),
            poBoxJurisdictionCaptor.capture(),
            caseTypeIdCaptor.capture(),
            existingCaseIdCaptor.capture(),
            ignoreWarningsCaptor.capture(),
            caseDataContentCaptor.capture()
        );
        assertThat(idamTokenCaptor.getValue()).isEqualTo(IDAM_TOKEN);
        assertThat(s2sTokenCaptor.getValue()).isEqualTo(S2S_TOKEN);
        assertThat(userIdCaptor.getValue()).isEqualTo(USER_ID);
        assertThat(poBoxJurisdictionCaptor.getValue()).isEqualTo(PO_BOX_JURISDICTION);
        assertThat(caseTypeIdCaptor.getValue()).isEqualTo(CASE_TYPE_ID);
        assertThat(existingCaseIdCaptor.getValue()).isEqualTo(EXISTING_CASE_ID_STR);
        assertThat(ignoreWarningsCaptor.getValue()).isEqualTo(IGNORE_WARNINGS);
        assertThat(caseDataContentCaptor.getValue().getCaseReference()).isEqualTo(EXCEPTION_RECORD_ID);
        assertThat(caseDataContentCaptor.getValue().getData()).isEqualTo(CASE_DATA);
        assertThat(caseDataContentCaptor.getValue().getEventToken()).isEqualTo(EVENT_TOKEN);
        assertThat(ex.getMessage()).isEqualTo("Service response: " + BODY);
    }

    private BDDMockito.BDDMyOngoingStubbing<CaseDetails> prepareEventSubmissionForCaseWorker() {
        return given(coreCaseDataApi.submitEventForCaseWorker(
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyBoolean(),
            any()
        ));
    }
}
