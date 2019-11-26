package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGenerator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.caseupdate.CaseUpdateClient;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.caseupdate.model.response.CaseUpdateDetails;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.caseupdate.model.response.SuccessfulUpdateResponse;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.request.ExceptionRecord;
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.ServiceConfigItem;
import uk.gov.hmcts.reform.ccd.client.CoreCaseDataApi;
import uk.gov.hmcts.reform.ccd.client.model.CaseDataContent;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.ccd.client.model.StartEventResponse;

import java.util.ArrayList;
import java.util.HashMap;

import static java.time.LocalDateTime.now;
import static java.util.Collections.emptyList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
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
    }

    @Test
    void should_update_case() throws Exception {
        // given
        given(configItem.getService()).willReturn("Service");
        given(existingCase.getId()).willReturn(1L);
        given(existingCase.getCaseTypeId()).willReturn("caseTypeId");
        given(configItem.getUpdateUrl()).willReturn("url");
        given(authTokenGenerator.generate()).willReturn("token");
        caseUpdateDetails = new CaseUpdateDetails("event_id", new HashMap<String, String>());
        successfulUpdateResponse = new SuccessfulUpdateResponse(caseUpdateDetails, new ArrayList<>());
        exceptionRecord = new ExceptionRecord(
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
        given(caseUpdateClient.updateCase("url", existingCase, exceptionRecord, "token"))
            .willReturn(successfulUpdateResponse);
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

        // when
        ccdCaseUpdater.updateCase(
            exceptionRecord,
            configItem,
            "idamToken",
            "userId",
            existingCase
        );

        // then

    }
}
