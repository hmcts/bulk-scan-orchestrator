package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.envelopehandlers;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.SupplementaryEvidence;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.mappers.SupplementaryEvidenceMapper;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CcdApi;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Envelope;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.exceptions.UnrecoverableErrorException;
import uk.gov.hmcts.reform.ccd.client.model.CaseDataContent;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.ccd.client.model.StartEventResponse;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.AUTH_DETAILS;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.fileContentAsBytes;

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

    @SuppressWarnings("unchecked")
    @Test
    void should_start_and_submit_event_for_valid_envelope() {
        // given
        given(ccdApi.authenticateJurisdiction(any())).willReturn(AUTH_DETAILS);

        var supplementaryEvidence = mock(SupplementaryEvidence.class);
        given(mapper.map(any(), any(), any())).willReturn(supplementaryEvidence);

        // and
        List<Map<String, Object>> existingEnvelopeReferences = mock(List.class);
        StartEventResponse startEventResponse = mock(StartEventResponse.class);
        given(startEventResponse.getCaseDetails())
            .willReturn(
                CaseDetails.builder().data(ImmutableMap.of("bulkScanEnvelopes", existingEnvelopeReferences)).build()
            );

        CaseDetails caseDetails = mock(CaseDetails.class);
        given(caseDetails.getCaseTypeId()).willReturn(SampleData.BULK_SCANNED_CASE_TYPE);

        Map<String, Object> ccdData = new HashMap<>();
        ccdData.put("scannedDocuments", emptyList());

        String eventToken = "token123";

        given(startEventResponse.getToken()).willReturn(eventToken);
        given(startEventResponse.getCaseDetails()).willReturn(caseDetails);
        given(startEventResponse.getCaseDetails().getData()).willReturn(ccdData);

        String caseId = "1539007368674134";
        given(caseDetails.getId()).willReturn(Long.parseLong(caseId));

        Envelope envelope = SampleData.envelope(2);

        given(mapper.getDocsToAdd(any(), any())).willReturn(envelope.documents);

        // when
        boolean docsAttached = attacher.attach(envelope, caseDetails);

        // then
        var caseDataBuilderCaptor = ArgumentCaptor.forClass(Function.class);

        verify(ccdApi).attachScannedDocs(
            eq(AUTH_DETAILS),
            eq(envelope.jurisdiction),
            eq(CASE_TYPE_ID),
            eq(caseId),
            eq(EVENT_TYPE_ID),
            caseDataBuilderCaptor.capture(),
            anyString()
        );

        var caseDataContent = (CaseDataContent) caseDataBuilderCaptor.getValue().apply(startEventResponse);
        assertThat(caseDataContent.getEventToken()).isEqualTo(eventToken);
        assertThat(caseDataContent.getEvent().getId()).isEqualTo(EVENT_TYPE_ID);
        assertThat(caseDataContent.getEvent().getSummary()).isEqualTo("Attach scanned documents");
        assertThat(caseDataContent.getData()).isSameAs(supplementaryEvidence);
        assertThat(docsAttached).isTrue();

        // and
        verify(mapper).map(emptyList(), null, envelope);
    }

    @SuppressWarnings("unchecked")
    @Test
    void should_start_and_submit_event_for_valid_envelope_if_existing_documents_are_corrupted() throws Exception {
        // given
        given(ccdApi.authenticateJurisdiction(any())).willReturn(AUTH_DETAILS);

        // and
        List<Map<String, Object>> existingEnvelopeReferences = mock(List.class);
        StartEventResponse startEventResponse = mock(StartEventResponse.class);
        given(startEventResponse.getCaseDetails())
            .willReturn(
                CaseDetails.builder().data(ImmutableMap.of("bulkScanEnvelopes", existingEnvelopeReferences)).build()
            );

        CaseDetails caseDetails = mock(CaseDetails.class);
        given(caseDetails.getCaseTypeId()).willReturn(SampleData.BULK_SCANNED_CASE_TYPE);

        Map<String, Object> ccdData = new HashMap<>();
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS);
        Map<String, Object>[] scannedDocuments = objectMapper.readValue(
            fileContentAsBytes("case-data/corrupted-scanned-docs.json"),
            Map[].class
        );
        ccdData.put("scannedDocuments", asList(scannedDocuments));

        given(startEventResponse.getCaseDetails()).willReturn(caseDetails);
        given(startEventResponse.getCaseDetails().getData()).willReturn(ccdData);

        String caseId = "1539007368674134";
        given(caseDetails.getId()).willReturn(Long.parseLong(caseId));

        Envelope envelope = SampleData.envelope(2);

        given(mapper.getDocsToAdd(any(), any())).willReturn(envelope.documents);

        // when
        attacher.attach(envelope, caseDetails);

        // then
        var caseDataBuilderCaptor = ArgumentCaptor.forClass(Function.class);

        verify(ccdApi).attachScannedDocs(
            eq(AUTH_DETAILS),
            eq(envelope.jurisdiction),
            eq(CASE_TYPE_ID),
            eq(caseId),
            eq(EVENT_TYPE_ID),
            caseDataBuilderCaptor.capture(),
            anyString()
        );

        assertThatThrownBy(() ->
            caseDataBuilderCaptor.getValue().apply(startEventResponse)
        )
            .isInstanceOf(UnrecoverableErrorException.class)
            .hasMessageContaining("File name of an existing document is NULL.");
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
        verify(ccdApi, never()).attachScannedDocs(any(), any(), any(), any(), any(), any(), any());
    }
}
