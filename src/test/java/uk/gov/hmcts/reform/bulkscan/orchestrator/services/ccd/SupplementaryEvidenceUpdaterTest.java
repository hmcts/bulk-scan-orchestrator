package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.internal.ExceptionRecord;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.callback.AttachScannedDocumentsValidator;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.ccd.client.model.StartEventResponse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CallbackValidationsTest.JOURNEY_CLASSIFICATION;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.ExceptionRecordFields.CONTAINS_PAYMENTS;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.ExceptionRecordFields.ENVELOPE_ID;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.ExceptionRecordFields.OCR_DATA;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.ExceptionRecordFields.SCANNED_DOCUMENTS;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.ExceptionRecordFields.SEARCH_CASE_REFERENCE;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.YesNoFieldValues.YES;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification.SUPPLEMENTARY_EVIDENCE;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification.SUPPLEMENTARY_EVIDENCE_WITH_OCR;

@ExtendWith(MockitoExtension.class)
class SupplementaryEvidenceUpdaterTest {

    @Mock
    private CcdApi ccdApi;

    @Mock
    private AttachScannedDocumentsValidator scannedDocumentsValidator;

    private SupplementaryEvidenceUpdater supplementaryEvidenceUpdater;

    private ExceptionRecord exceptionRecord;

    private static final String JURISDICTION = "BULKSCAN";
    private static final String SERVICE_NAME = "bulkscan";

    private static final String IDAM_TOKEN = "IDAM_TOKEN";
    private static final String USER_ID = "USER_ID";
    private static final String EXISTING_CASE_ID = "12345";
    private static final String EXISTING_CASE_TYPE = "Bulk_Scanned";

    private static final String BULKSCAN_ENVELOPE_ID = "some-envelope-id";
    private static final String CASE_REF = "1539007368674134";
    private static final String CASE_TYPE_EXCEPTION_RECORD = "BULKSCAN_ExceptionRecord";
    private static final String DOCUMENT_FILENAME = "document.pdf";
    private static final String DOCUMENT_NUMBER = "123456";
    private static final Map<String, Object> EXISTING_DOC = document(DOCUMENT_FILENAME, DOCUMENT_NUMBER);

    private static final CaseDetails CASE_DETAILS = CaseDetails.builder()
        .jurisdiction(JURISDICTION)
        .caseTypeId(CASE_TYPE_EXCEPTION_RECORD)
        .id(Long.parseLong(CASE_REF))
        .data(ImmutableMap.<String, Object>builder()
            .put(JOURNEY_CLASSIFICATION, SUPPLEMENTARY_EVIDENCE_WITH_OCR.name())
            .put(SEARCH_CASE_REFERENCE, EXISTING_CASE_ID)
            .put(OCR_DATA, singletonList(ImmutableMap.of("firstName", "John")))
            .put(SCANNED_DOCUMENTS, ImmutableList.of(EXISTING_DOC))
            .put(CONTAINS_PAYMENTS, YES)
            .put(ENVELOPE_ID, BULKSCAN_ENVELOPE_ID)
            .build()
        )
        .build();

    @BeforeEach
    public void setUp() {
        supplementaryEvidenceUpdater = new SupplementaryEvidenceUpdater(
            ccdApi,
            scannedDocumentsValidator
        );
    }

    @Test
    void should_update_supplementary_evidence() {
        // given
        List<Map<String, Object>> exceptionRecordDocuments = new ArrayList<>();
        Map<String, Object> doc1 = new HashMap<>();
        doc1.put("value", singletonMap("exceptionRecordReference", "1539007368674134"));
        exceptionRecordDocuments.add(doc1);

        List<Map<String, Object>> existingScannedDocuments = new ArrayList<>();
        Map<String, Object> doc2 = new HashMap<>();
        existingScannedDocuments.add(doc2);
        Map<String, Object> existingData = new HashMap<>();
        existingData.put("scannedDocuments", existingScannedDocuments);

        final CaseDetails existingCaseDetails = CaseDetails.builder()
            .jurisdiction(JURISDICTION)
            .caseTypeId(EXISTING_CASE_TYPE)
            .id(Long.parseLong(EXISTING_CASE_ID))
            .data(existingData)
            .build();
        given(ccdApi.getCase(EXISTING_CASE_ID, JURISDICTION)).willReturn(existingCaseDetails);
        StartEventResponse startEventResponse = mock(StartEventResponse.class);
        given(ccdApi.startAttachScannedDocs(existingCaseDetails, IDAM_TOKEN, USER_ID))
            .willReturn(startEventResponse);
        AttachToCaseEventData callBackEvent = getCallbackEvent(exceptionRecordDocuments);

        // when
        supplementaryEvidenceUpdater.updateSupplementaryEvidence(callBackEvent, EXISTING_CASE_ID);

        // then
        verify(scannedDocumentsValidator)
            .verifyExceptionRecordAddsNoDuplicates(anyList(), anyList(), eq(CASE_REF), eq(EXISTING_CASE_ID));
        verify(ccdApi).getCase(EXISTING_CASE_ID, JURISDICTION);
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
}
