package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.cdam.CdamApiClient;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.request.DocumentType;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.request.DocumentUrl;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.request.ScannedDocument;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.internal.ExceptionRecord;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.callback.AttachScannedDocumentsValidator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.callback.DuplicateDocsException;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.ccd.client.model.StartEventResponse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.time.LocalDateTime.now;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.ExceptionRecordFields.SCANNED_DOCUMENTS;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification.SUPPLEMENTARY_EVIDENCE;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification.SUPPLEMENTARY_EVIDENCE_WITH_OCR;

@ExtendWith(MockitoExtension.class)
class SupplementaryEvidenceUpdaterTest {

    @Mock
    private CcdApi ccdApi;

    @Mock
    private AttachScannedDocumentsValidator scannedDocumentsValidator;

    @Mock
    private CdamApiClient cdamApiClient;

    private SupplementaryEvidenceUpdater supplementaryEvidenceUpdater;

    private ExceptionRecord exceptionRecord;

    private static final String EXCEPTION_RECORD_REFERENCE = "exceptionRecordReference";
    private static final String VALUE = "value";

    private static final String JURISDICTION = "BULKSCAN";
    private static final String SERVICE_NAME = "bulkscan";

    private static final String IDAM_TOKEN = "IDAM_TOKEN";
    private static final String USER_ID = "USER_ID";
    private static final String EXISTING_CASE_ID = "12345";
    private static final String EXISTING_CASE_TYPE = "Bulk_Scanned";

    private static final String CASE_REF = "1539007368674134";

    @BeforeEach
    public void setUp() {
        supplementaryEvidenceUpdater = new SupplementaryEvidenceUpdater(
            ccdApi,
            scannedDocumentsValidator,
            cdamApiClient
        );

        exceptionRecord = getExceptionRecord();
    }

    @Test
    void should_update_case_if_there_are_documents_to_attach() {
        // given
        List<Map<String, Object>> existingScannedDocuments = new ArrayList<>();
        Map<String, Object> doc2 = new HashMap<>();
        existingScannedDocuments.add(doc2);
        Map<String, Object> existingData = new HashMap<>();
        existingData.put(SCANNED_DOCUMENTS, existingScannedDocuments);

        final CaseDetails existingCaseDetails = CaseDetails.builder()
            .jurisdiction(JURISDICTION)
            .caseTypeId(EXISTING_CASE_TYPE)
            .id(Long.parseLong(EXISTING_CASE_ID))
            .data(existingData)
            .build();
        StartEventResponse startEventResponse = mock(StartEventResponse.class);
        given(ccdApi.startAttachScannedDocs(existingCaseDetails, IDAM_TOKEN, USER_ID))
            .willReturn(startEventResponse);

        List<Map<String, Object>> exceptionRecordDocuments = new ArrayList<>();
        Map<String, Object> doc1 = new HashMap<>();
        Map<String, Object> docUrl = new HashMap<>();
        docUrl.put("document_url", "http://localhost/uuid1");
        doc1.put(VALUE, Map.of(EXCEPTION_RECORD_REFERENCE, CASE_REF, "url", docUrl));

        exceptionRecordDocuments.add(doc1);

        var hashToken1 = "321hhjRETE31321dsds";
        given(cdamApiClient.getDocumentHash(JURISDICTION, "uuid1"))
            .willReturn(hashToken1);

        AttachToCaseEventData callBackEvent = getCallbackEvent(exceptionRecordDocuments);

        // when
        supplementaryEvidenceUpdater.updateSupplementaryEvidence(
            callBackEvent,
            existingCaseDetails,
            EXISTING_CASE_ID
        );

        // then
        verify(scannedDocumentsValidator)
            .verifyExceptionRecordAddsNoDuplicates(anyList(), anyList(), eq(CASE_REF), eq(EXISTING_CASE_ID));
        verify(ccdApi).startAttachScannedDocs(any(CaseDetails.class), eq(IDAM_TOKEN), eq(USER_ID));
        verify(ccdApi).attachExceptionRecord(
            eq(existingCaseDetails),
            eq(IDAM_TOKEN),
            eq(USER_ID),
            anyMap(),
            eq("Attaching exception record(" + CASE_REF + ") document numbers:[] to case:" + EXISTING_CASE_ID),
            eq(startEventResponse)
        );
    }

    @Test
    void should_not_update_case_if_there_are_duplicated_documents() {
        // given
        List<Map<String, Object>> existingScannedDocuments = emptyList();
        Map<String, Object> existingData = new HashMap<>();
        existingData.put(SCANNED_DOCUMENTS, existingScannedDocuments);

        final CaseDetails existingCaseDetails = CaseDetails.builder()
            .jurisdiction(JURISDICTION)
            .caseTypeId(EXISTING_CASE_TYPE)
            .id(Long.parseLong(EXISTING_CASE_ID))
            .data(existingData)
            .build();

        doThrow(new DuplicateDocsException("msg"))
            .when(scannedDocumentsValidator)
            .verifyExceptionRecordAddsNoDuplicates(anyList(), anyList(), eq(CASE_REF), eq(EXISTING_CASE_ID));

        List<Map<String, Object>> exceptionRecordDocuments = emptyList();

        AttachToCaseEventData callBackEvent = getCallbackEvent(exceptionRecordDocuments);

        // when
        // then
        assertThatCode(() ->
            supplementaryEvidenceUpdater.updateSupplementaryEvidence(
                callBackEvent,
                existingCaseDetails,
                EXISTING_CASE_ID
            ))
            .isInstanceOf(DuplicateDocsException.class)
            .hasMessage("msg");

        // then
        verify(scannedDocumentsValidator)
            .verifyExceptionRecordAddsNoDuplicates(anyList(), anyList(), eq(CASE_REF), eq(EXISTING_CASE_ID));
        verifyNoMoreInteractions(ccdApi);
    }

    @Test
    void should_not_update_case_if_there_are_no_documents_to_attach() {
        // given
        List<Map<String, Object>> existingScannedDocuments = emptyList();
        Map<String, Object> existingData = new HashMap<>();
        existingData.put(SCANNED_DOCUMENTS, existingScannedDocuments);

        final CaseDetails existingCaseDetails = CaseDetails.builder()
            .jurisdiction(JURISDICTION)
            .caseTypeId(EXISTING_CASE_TYPE)
            .id(Long.parseLong(EXISTING_CASE_ID))
            .data(existingData)
            .build();

        List<Map<String, Object>> exceptionRecordDocuments = emptyList();

        AttachToCaseEventData callBackEvent = getCallbackEvent(exceptionRecordDocuments);

        // when
        supplementaryEvidenceUpdater.updateSupplementaryEvidence(
            callBackEvent,
            existingCaseDetails,
            EXISTING_CASE_ID
        );

        // then
        verify(scannedDocumentsValidator)
            .verifyExceptionRecordAddsNoDuplicates(anyList(), anyList(), eq(CASE_REF), eq(EXISTING_CASE_ID));
        verifyNoMoreInteractions(ccdApi);
    }

    private static Map<String, Object> document(String filename, String documentNumber) {
        return ImmutableMap.of(
            "value", ImmutableMap.of(
                "fileName", filename,
                "controlNumber", documentNumber,
                "someNumber", 3
            )
        );
    }

    private AttachToCaseEventData getCallbackEvent(List<Map<String, Object>> exceptionRecordDocuments) {
        return new AttachToCaseEventData(
            JURISDICTION,
            SERVICE_NAME,
            EXISTING_CASE_TYPE,
            EXISTING_CASE_ID,
            Long.parseLong(CASE_REF),
            exceptionRecordDocuments,
            IDAM_TOKEN,
            USER_ID,
            SUPPLEMENTARY_EVIDENCE,
            exceptionRecord
        );
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
