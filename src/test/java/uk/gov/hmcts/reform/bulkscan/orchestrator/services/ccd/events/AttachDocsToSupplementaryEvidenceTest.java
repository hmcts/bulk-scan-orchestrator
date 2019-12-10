package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.events;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.mappers.SupplementaryEvidenceMapper;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CcdApi;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Envelope;
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

@ExtendWith(MockitoExtension.class)
class AttachDocsToSupplementaryEvidenceTest {

    private static final String CASE_TYPE_ID = "Bulk_Scanned";
    private static final String EVENT_TYPE_ID = "attachScannedDocs";

    @Mock
    private CcdApi ccdApi;

    @Mock
    private SupplementaryEvidenceMapper mapper;

    private AttachDocsToSupplementaryEvidence attacher;

    @BeforeEach
    void setUp() {
        this.attacher = new AttachDocsToSupplementaryEvidence(mapper, ccdApi);
    }

    @Test
    void should_start_and_submit_event_for_valid_envelope() {
        // given
        given(ccdApi.authenticateJurisdiction(any())).willReturn(AUTH_DETAILS);

        // and
        String eventToken = "token123";

        StartEventResponse startEventResponse = mock(StartEventResponse.class);
        CaseDetails caseDetails = mock(CaseDetails.class);
        given(caseDetails.getCaseTypeId()).willReturn(SampleData.BULK_SCANNED_CASE_TYPE);

        Map<String, Object> ccdData = new HashMap<>();
        ccdData.put("scannedDocuments", emptyList());

        given(startEventResponse.getToken()).willReturn(eventToken);
        given(startEventResponse.getCaseDetails()).willReturn(caseDetails);
        given(startEventResponse.getCaseDetails().getData()).willReturn(ccdData);

        given(ccdApi.startEventForAttachScannedDocs(any(), any(), any(), any(), any()))
            .willReturn(startEventResponse);
        given(ccdApi.submitEventForAttachScannedDocs(any(), any(), any(), any(), any())).willReturn(caseDetails);

        String caseId = "1539007368674134";
        given(caseDetails.getId()).willReturn(Long.parseLong(caseId));

        Envelope envelope = SampleData.envelope(2);

        given(mapper.getDocsToAdd(any(), any())).willReturn(envelope.documents);

        // when
        boolean docsAttached = attacher.attach(envelope, caseDetails);

        // then
        verify(ccdApi).startEventForAttachScannedDocs(
            AUTH_DETAILS,
            envelope.jurisdiction,
            CASE_TYPE_ID,
            caseId,
            EVENT_TYPE_ID
        );
        ArgumentCaptor<CaseDataContent> caseDataContentCaptor = ArgumentCaptor.forClass(CaseDataContent.class);

        verify(ccdApi).submitEventForAttachScannedDocs(
            eq(AUTH_DETAILS),
            eq(envelope.jurisdiction),
            eq(CASE_TYPE_ID),
            eq(caseId),
            caseDataContentCaptor.capture()
        );

        verify(mapper).map(emptyList(), envelope.documents, envelope.deliveryDate);

        CaseDataContent caseDataContent = caseDataContentCaptor.getValue();
        assertThat(caseDataContent.getEventToken()).isEqualTo(eventToken);
        assertThat(caseDataContent.getEvent().getId()).isEqualTo(EVENT_TYPE_ID);
        assertThat(caseDataContent.getEvent().getSummary()).isEqualTo("Attach scanned documents");
        assertThat(docsAttached).isTrue();
    }

    @Test
    void should_not_start_an_event_if_envelope_does_not_contain_any_new_documents() {
        // given
        CaseDetails existingCase = mock(CaseDetails.class);
        Envelope envelope = SampleData.envelope(2);

        given(mapper.getDocsToAdd(any(), any()))
            .willReturn(emptyList()); // no new docs

        // when
        attacher.attach(envelope, existingCase);

        // then
        verify(ccdApi, never()).startEvent(any(), any(), any(), any());
    }
}
