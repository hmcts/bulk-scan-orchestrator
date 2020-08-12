package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.callback;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.request.DocumentType;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static uk.gov.hmcts.reform.bulkscan.orchestrator.DatetimeHelper.toIso8601;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.JURSIDICTION;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.PO_BOX;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.ExceptionRecordFields.CONTAINS_PAYMENTS;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.ExceptionRecordFields.JOURNEY_CLASSIFICATION;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.ExceptionRecordFields.OCR_DATA;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.ExceptionRecordFields.SCANNED_DOCUMENTS;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.ExceptionRecordFields.SEARCH_CASE_REFERENCE;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.YesNoFieldValues.YES;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification.EXCEPTION;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification.NEW_APPLICATION;

public class TestExceptionRecordCaseBuilder {
    private TestExceptionRecordCaseBuilder() {
    }

    static CaseDetails createValidExceptionRecordCase() {
        return createCaseWith(JURSIDICTION, "BULKSCAN_ExceptionRecord", 1234L, validCaseData());
    }

    static CaseDetails caseWithId(Long caseId) {
        return createCaseWith(JURSIDICTION, "BULKSCAN_ExceptionRecord", caseId, validCaseData());
    }

    static CaseDetails caseWithType(String caseType) {
        return createCaseWith(JURSIDICTION, caseType, 1234L, validCaseData());
    }

    static CaseDetails caseWithJurisdiction(String jurisdiction) {
        return createCaseWith(jurisdiction, "BULKSCAN_ExceptionRecord", 1234L, validCaseData());
    }

    static CaseDetails caseWithData(String key, Object value) {
        Map<String, Object> caseData = validCaseData();
        caseData.put(key, value);
        return createCaseWith(EXCEPTION.name(), "BULKSCAN_ExceptionRecord", 1234L, caseData);
    }

    static CaseDetails caseWithFormTypeAndClassification(String formType, String classification) {
        Map<String, Object> caseData = validCaseData();
        caseData.replace("formType", formType);
        caseData.replace(JOURNEY_CLASSIFICATION, classification);
        return createCaseWith(JURSIDICTION, "BULKSCAN_ExceptionRecord", 1234L, caseData);
    }

    static CaseDetails caseWithScannedDocumentData(String key, Object value) {
        Map<String, Object> document = document();
        document.put(key, value);
        Map<String, Object> caseData = validCaseData();
        caseData.put(SCANNED_DOCUMENTS, ImmutableList.of(ImmutableMap.of("value", document)));
        return createCaseWith(EXCEPTION.name(), "BULKSCAN_ExceptionRecord", 1234L, caseData);
    }

    private static CaseDetails createCaseWith(
        String jurisdiction,
        String caseType,
        Long caseId,
        Map<String, Object> data
    ) {
        return CaseDetails.builder()
            .jurisdiction(jurisdiction)
            .caseTypeId(caseType)
            .id(caseId)
            .data(data)
            .build();
    }

    private static Map<String, Object> validCaseData() {
        Map<String, Object> data = new HashMap<>();
        data.put("poBox", PO_BOX);
        data.put(JOURNEY_CLASSIFICATION, NEW_APPLICATION.name());
        data.put("deliveryDate", toIso8601(Instant.now()));
        data.put("openingDate", toIso8601(Instant.now()));
        data.put(SEARCH_CASE_REFERENCE, "123");
        data.put("formType", "personal");
        data.put(
            OCR_DATA,
            ImmutableList.of(ImmutableMap.of("value", ImmutableMap.of("key", "firstName", "value", "John")))
        );
        data.put(SCANNED_DOCUMENTS, ImmutableList.of(ImmutableMap.of("value", document())));
        data.put(CONTAINS_PAYMENTS, YES);
        data.put("envelopeId", "envelopeId123");
        return data;
    }

    private static Map<String, Object> document() {
        Map<String, Object> document = new HashMap<>();
        document.put("fileName", "file1.pdf");
        document.put("controlNumber", "12341234");
        document.put("type", DocumentType.FORM.name());
        document.put("subtype", "personal");
        document.put("url", ImmutableMap.of(
            "document_url", "http://locahost",
            "document_binary_url", "http://locahost/binary",
            "document_filename", "file1.pdf"
            )
        );
        document.put("scannedDate", toIso8601(Instant.now()));
        document.put("deliveryDate", toIso8601(Instant.now()));
        return document;
    }

}
