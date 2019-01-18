package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.events;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.SupplementaryEvidence;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.mappers.SupplementaryEvidenceMapper;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CcdAuthenticatorFactory;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;
import uk.gov.hmcts.reform.ccd.client.CoreCaseDataApi;
import uk.gov.hmcts.reform.ccd.client.model.CaseDataContent;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.ccd.client.model.StartEventResponse;

import java.util.HashMap;
import java.util.Map;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.AUTH_DETAILS;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.SERVICE_TOKEN;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.USER_DETAILS;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.USER_TOKEN;

@RunWith(MockitoJUnitRunner.class)
public class AttachDocsToSupplementaryEvidenceTest {

    private static final String CASE_TYPE_ID = "Bulk_Scanned";
    private static final String EVENT_TYPE_ID = "attachScannedDocs";

    @Mock
    private CcdAuthenticatorFactory authenticatorFactory;

    @Mock
    private CoreCaseDataApi coreCaseDataApi;

    @InjectMocks
    private EventPublisher eventPublisher = new AttachDocsToSupplementaryEvidence();

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        given(authenticatorFactory.createForJurisdiction(any())).willReturn(AUTH_DETAILS);
    }

    @Test
    public void createSupplementaryEvidence_starts_and_submits_event() {
        String eventToken = "token123";

        StartEventResponse startEventResponse = mock(StartEventResponse.class);
        CaseDetails caseDetails = mock(CaseDetails.class);

        Map<String, Object> ccdData = new HashMap<>();
        ccdData.put("scannedDocuments", emptyList());

        given(startEventResponse.getToken()).willReturn(eventToken);
        given(startEventResponse.getCaseDetails()).willReturn(caseDetails);
        given(startEventResponse.getCaseDetails().getData()).willReturn(ccdData);

        given(coreCaseDataApi.startEventForCaseWorker(any(), any(), any(), any(), any(), any(), any()))
            .willReturn(startEventResponse);

        Envelope envelope = SampleData.envelope(2);

        eventPublisher.publish(envelope, SampleData.BULK_SCANNED_CASE_TYPE);

        verifyEventStarted(envelope);
        verifyEventSubmitted(envelope, eventToken);
    }

    private void assertCaseDataContentHasRightData(
        CaseDataContent caseDataContent,
        String eventToken,
        Envelope envelope
    ) {
        assertThat(caseDataContent.getEventToken()).isEqualTo(eventToken);
        assertThat(caseDataContent.getEvent().getId()).isEqualTo(EVENT_TYPE_ID);
        assertThat(caseDataContent.getEvent().getSummary()).isEqualTo("Attach scanned documents");

        SupplementaryEvidence supplementaryEvidence = SupplementaryEvidenceMapper.mapEnvelope(envelope);

        assertThat(caseDataContent.getData()).isEqualToComparingFieldByFieldRecursively(supplementaryEvidence);
    }

    private void verifyEventStarted(Envelope envelope) {
        verify(coreCaseDataApi).startEventForCaseWorker(
            USER_TOKEN,
            SERVICE_TOKEN,
            USER_DETAILS.getId(),
            envelope.jurisdiction,
            CASE_TYPE_ID,
            envelope.caseRef,
            EVENT_TYPE_ID
        );
    }

    private void verifyEventSubmitted(Envelope envelope, String eventToken) {
        ArgumentCaptor<CaseDataContent> caseDataContentCaptor =
            ArgumentCaptor.forClass(CaseDataContent.class);

        verify(coreCaseDataApi).submitEventForCaseWorker(
            eq(USER_TOKEN),
            eq(SERVICE_TOKEN),
            eq(USER_DETAILS.getId()),
            eq(envelope.jurisdiction),
            eq(CASE_TYPE_ID),
            eq(envelope.caseRef),
            eq(true),
            caseDataContentCaptor.capture()
        );

        assertCaseDataContentHasRightData(caseDataContentCaptor.getValue(), eventToken, envelope);
    }
}
