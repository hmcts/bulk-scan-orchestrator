package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.SupplementaryEvidence;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.mappers.SupplementaryEvidenceMapper;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;
import uk.gov.hmcts.reform.ccd.client.CoreCaseDataApi;
import uk.gov.hmcts.reform.ccd.client.model.CaseDataContent;
import uk.gov.hmcts.reform.ccd.client.model.StartEventResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.AUTH_DETAILS;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.SERVICE_TOKEN;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.USER_DETAILS;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.USER_TOKEN;

@RunWith(MockitoJUnitRunner.class)
public class SupplementaryEvidenceCreatorTest {

    private static final String CASE_TYPE_ID = "Bulk_Scanned";
    private static final String EVENT_TYPE_ID = "attachScannedDocs";

    @Mock
    private CcdAuthenticatorFactory authenticatorFactory;

    @Mock
    private CoreCaseDataApi coreCaseDataApi;

    private SupplementaryEvidenceCreator creator;

    @Before
    public void setUp() {
        given(authenticatorFactory.createForJurisdiction(any())).willReturn(AUTH_DETAILS);

        creator = new SupplementaryEvidenceCreator(authenticatorFactory, coreCaseDataApi);
    }

    @Test
    public void createSupplementaryEvidence_starts_and_submits_event() {
        Envelope envelope = SampleData.envelope(2);
        String eventToken = "token123";

        StartEventResponse startEventResponse = mock(StartEventResponse.class);
        given(startEventResponse.getToken()).willReturn(eventToken);

        given(coreCaseDataApi.startEventForCaseWorker(any(), any(), any(), any(), any(), any(), any()))
            .willReturn(startEventResponse);

        creator.createSupplementaryEvidence(envelope);

        verifyEventStarted(envelope);
        verifyEventSubmitted(envelope, eventToken);
    }

    @Test
    public void createSupplementaryEvidence_does_not_submit_event_when_starting_fails() {
        Exception expectedException = new RuntimeException("test exception");

        willThrow(expectedException)
            .given(coreCaseDataApi)
            .startEventForCaseWorker(any(), any(), any(), any(), any(), any(), any());

        Throwable actualException = catchThrowable(
            () -> creator.createSupplementaryEvidence(SampleData.envelope(1))
        );

        assertThat(actualException).isSameAs(expectedException);

        verify(coreCaseDataApi, never())
            .submitEventForCaseWorker(any(), any(), any(), any(), any(), any(), eq(true), any());
    }

    private void assertCaseDataContentHasRightData(
        CaseDataContent caseDataContent,
        String eventToken,
        Envelope envelope
    ) {
        assertThat(caseDataContent.getEventToken()).isEqualTo(eventToken);
        assertThat(caseDataContent.getEvent().getId()).isEqualTo(EVENT_TYPE_ID);
        assertThat(caseDataContent.getEvent().getSummary()).isEqualTo("Attach scanned documents");
        assertThat(caseDataContent.getEvent().getDescription()).isEqualTo("Attach scanned documents");

        SupplementaryEvidence supplementaryEvidence = SupplementaryEvidenceMapper.fromEnvelope(envelope);

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
