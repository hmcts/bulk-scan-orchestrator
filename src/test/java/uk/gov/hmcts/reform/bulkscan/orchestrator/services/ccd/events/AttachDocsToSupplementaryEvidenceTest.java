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
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.mappers.SupplementaryEvidenceMapper;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CcdApi;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.AUTH_DETAILS;

@RunWith(MockitoJUnitRunner.class)
public class AttachDocsToSupplementaryEvidenceTest {

    private static final String CASE_TYPE_ID = "Bulk_Scanned";
    private static final String EVENT_TYPE_ID = "attachScannedDocs";

    @Mock
    private CcdApi ccdApi;

    private SupplementaryEvidenceMapper mapper = mock(SupplementaryEvidenceMapper.class);

    @InjectMocks
    private AttachDocsToSupplementaryEvidence eventPublisher = new AttachDocsToSupplementaryEvidence(mapper);

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        given(ccdApi.authenticateJurisdiction(any())).willReturn(AUTH_DETAILS);
    }

    @Test
    public void createSupplementaryEvidence_starts_and_submits_event() {
        // given
        String eventToken = "token123";

        StartEventResponse startEventResponse = mock(StartEventResponse.class);
        CaseDetails caseDetails = mock(CaseDetails.class);
        given(caseDetails.getCaseTypeId()).willReturn(SampleData.BULK_SCANNED_CASE_TYPE);

        Map<String, Object> ccdData = new HashMap<>();
        ccdData.put("scannedDocuments", emptyList());

        given(startEventResponse.getToken()).willReturn(eventToken);
        given(startEventResponse.getCaseDetails()).willReturn(caseDetails);
        given(startEventResponse.getCaseDetails().getData()).willReturn(ccdData);

        given(ccdApi.startEvent(any(), any(), any(), any(), any())).willReturn(startEventResponse);
        given(ccdApi.submitEvent(any(), any(), any(), any(), any())).willReturn(caseDetails);

        given(caseDetails.getId()).willReturn(1L);

        Envelope envelope = SampleData.envelope(2);

        given(mapper.getDocsToAdd(any(), any())).willReturn(envelope.documents);

        // when
        eventPublisher.publish(envelope, caseDetails);

        // then
        verify(ccdApi).startEvent(
            AUTH_DETAILS,
            envelope.jurisdiction,
            CASE_TYPE_ID,
            envelope.caseRef,
            EVENT_TYPE_ID
        );
        ArgumentCaptor<CaseDataContent> caseDataContentCaptor = ArgumentCaptor.forClass(CaseDataContent.class);

        verify(ccdApi).submitEvent(
            eq(AUTH_DETAILS),
            eq(envelope.jurisdiction),
            eq(CASE_TYPE_ID),
            eq(envelope.caseRef),
            caseDataContentCaptor.capture()
        );

        verify(mapper).map(emptyList(), envelope.documents);

        CaseDataContent caseDataContent = caseDataContentCaptor.getValue();
        assertThat(caseDataContent.getEventToken()).isEqualTo(eventToken);
        assertThat(caseDataContent.getEvent().getId()).isEqualTo(EVENT_TYPE_ID);
        assertThat(caseDataContent.getEvent().getSummary()).isEqualTo("Attach scanned documents");
    }

    @Test
    public void should_not_start_an_event_if_envelope_does_not_contain_any_new_documents() {
        // given
        CaseDetails existingCase = mock(CaseDetails.class);
        Envelope envelope = SampleData.envelope(2);

        given(mapper.getDocsToAdd(any(), any()))
            .willReturn(emptyList()); // no new docs

        // when
        eventPublisher.publish(envelope, existingCase);

        // then
        verify(ccdApi, never()).startEvent(any(), any(), any(), any(), any());
    }
}
