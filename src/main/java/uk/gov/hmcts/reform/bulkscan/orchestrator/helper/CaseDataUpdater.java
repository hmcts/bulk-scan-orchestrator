package uk.gov.hmcts.reform.bulkscan.orchestrator.helper;

import com.google.common.collect.ImmutableMap;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.CaseAction;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.CcdCollectionElement;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.EnvelopeReference;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.ScannedDocument;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.internal.ExceptionRecord;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.EnvelopeReferenceHelper;

import java.util.List;
import java.util.Map;

import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Maps.newHashMap;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.ExceptionRecordFields.SCANNED_DOCUMENTS;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.ServiceCaseFields.BULK_SCAN_ENVELOPES;

@Component
public class CaseDataUpdater {

    private final EnvelopeReferenceHelper envelopeReferenceHelper;

    public CaseDataUpdater(EnvelopeReferenceHelper envelopeReferenceHelper) {
        this.envelopeReferenceHelper = envelopeReferenceHelper;
    }

    public Map<String, Object> setExceptionRecordIdToScannedDocuments(
        ExceptionRecord exceptionRecord,
        Map<String, Object> caseData
    ) {
        var updatedCaseData = newHashMap(caseData);
        List<ScannedDocument> scannedDocuments = getScannedDocuments(caseData);

        List<String> exceptionRecordDcns = exceptionRecord.scannedDocuments
            .stream()
            .map(scannedDocument -> scannedDocument.controlNumber)
            .collect(toList());

        List<ImmutableMap<String, ScannedDocument>> updatedScannedDocuments = scannedDocuments.stream()
            .map(scannedDocument -> {
                if (exceptionRecordDcns.contains(scannedDocument.controlNumber)) {
                    // set exceptionReference if the document received with the exception record
                    return ImmutableMap.of("value", new ScannedDocument(
                        scannedDocument.fileName,
                        scannedDocument.controlNumber,
                        scannedDocument.type,
                        scannedDocument.subtype,
                        scannedDocument.scannedDate,
                        scannedDocument.url,
                        scannedDocument.deliveryDate,
                        exceptionRecord.id
                    ));
                } else {
                    // do not update the document if it was received with some previous exception record
                    return ImmutableMap.of("value", scannedDocument);
                }
            })
            .collect(toList());

        updatedCaseData.put(SCANNED_DOCUMENTS, updatedScannedDocuments);

        return updatedCaseData;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> updateUpdateEnvelopeReferences(
        Map<String, Object> caseData,
        String envelopeId
    ) {
        List<CcdCollectionElement<EnvelopeReference>> existingCaseRefs =
            envelopeReferenceHelper.
                parseEnvelopeReferences(
                    (List<Map<String, Object>>) caseData.get(BULK_SCAN_ENVELOPES)
                );

        var updatedCaseRefs = newArrayList(existingCaseRefs);
        updatedCaseRefs.add(new CcdCollectionElement<>(new EnvelopeReference(envelopeId, CaseAction.UPDATE)));

        var updatedCaseData = newHashMap(caseData);
        updatedCaseData.put(BULK_SCAN_ENVELOPES, updatedCaseRefs);

        return updatedCaseData;
    }

    @SuppressWarnings("unchecked")
    static List<ScannedDocument> getScannedDocuments(Map<String, Object> caseData) {
        var scannedDocuments = (List<Map<String, Object>>) caseData.get(SCANNED_DOCUMENTS);

        return scannedDocuments == null
            ? emptyList()
            : scannedDocuments.stream()
            .map(ScannedDocumentsHelper::createScannedDocumentWithCcdData)
            .collect(toList());
    }
}
