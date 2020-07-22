package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails.CaseDetailsBuilder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.ExceptionRecordFields.AWAITING_PAYMENT_DCN_PROCESSING;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.ExceptionRecordFields.JOURNEY_CLASSIFICATION;

class TestCaseBuilder {
    private TestCaseBuilder() {
    }

    static CaseDetails createCaseWith(Function<CaseDetailsBuilder, CaseDetailsBuilder> builder) {
        return builder.apply(CaseDetails.builder()).build();
    }

    static CaseDetails caseWithAttachReference(Object caseReference) {
        Map<String, Object> data = new HashMap<>();
        data.put("attachToCaseReference", caseReference);
        return createCaseWith(b -> b.data(data));
    }

    static CaseDetails caseWithTargetReference(Object searchCaseReference) {
        Map<String, Object> data = new HashMap<>();
        data.put("searchCaseReference", searchCaseReference);
        return createCaseWith(b -> b.data(data));
    }

    static CaseDetails caseWithSearchCaseReferenceType(Object searchCaseReferenceType) {
        Map<String, Object> caseData = new HashMap<>();
        caseData.put("searchCaseReferenceType", searchCaseReferenceType);
        return createCaseWith(b -> b.data(caseData));
    }

    static CaseDetails caseWithCcdSearchCaseReference(Object searchCaseReference) {
        return caseWithSearchCaseRefTypeAndCaseRef("ccdCaseReference", searchCaseReference);
    }

    static CaseDetails caseWithExternalSearchCaseReference(Object searchCaseReference) {
        return caseWithSearchCaseRefTypeAndCaseRef("externalCaseReference", searchCaseReference);
    }

    static CaseDetails caseWithSearchCaseRefTypeAndCaseRef(
        Object searchCaseReferenceType, Object searchCaseReference
    ) {
        Map<String, Object> caseData = new HashMap<>();
        caseData.put("searchCaseReference", searchCaseReference);
        caseData.put("searchCaseReferenceType", searchCaseReferenceType);

        return createCaseWith(b -> b.data(caseData));
    }

    static CaseDetails caseWithAwaitingPaymentsAndClassification(
        Object awaitingPaymentsProcessing,
        Object classification
    ) {
        Map<String, Object> data = new HashMap<>();
        data.put(AWAITING_PAYMENT_DCN_PROCESSING, awaitingPaymentsProcessing);
        data.put(JOURNEY_CLASSIFICATION, classification);
        return createCaseWith(b -> b.data(data));
    }

    static CaseDetails caseWithDocument(List<Map<String, Object>> caseReference) {
        Map<String, Object> data = new HashMap<>();
        data.put("scannedDocuments", caseReference);
        return createCaseWith(b -> b.data(data));
    }

    static List<Map<String, Object>> document(String url, String name) {
        Map<String, Object> doc = new HashMap<>();

        doc.put("type", "Other");
        doc.put("url", ImmutableMap.of(
            "document_url", url,
            "document_binary_url", url,
            "document_filename", name
        ));
        doc.put("controlNumber", "1234");
        doc.put("fileName", "file");
        doc.put("scannedDate", "2019-09-06T15:40:00.000Z");
        doc.put("deliveryDate", "2019-09-06T15:40:00.001Z");

        return ImmutableList.of(ImmutableMap.of("value", doc));
    }

    static List<Map<String, Object>> ocrDataEntry(String key, String value) {
        return ImmutableList.of(ImmutableMap.of("value", ImmutableMap.of("key", key, "value", value)));
    }
}
