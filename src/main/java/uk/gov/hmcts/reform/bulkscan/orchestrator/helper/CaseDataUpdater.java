package uk.gov.hmcts.reform.bulkscan.orchestrator.helper;

import com.google.common.collect.ImmutableMap;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.ScannedDocument;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.internal.ExceptionRecord;

import java.util.List;
import java.util.Map;

import static com.google.common.collect.Maps.newHashMap;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.ExceptionRecordFields.SCANNED_DOCUMENTS;

@Component
public class CaseDataUpdater {

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
    static List<ScannedDocument> getScannedDocuments(Map<String, Object> caseData) {
        var scannedDocuments = (List<Map<String, Object>>) caseData.get(SCANNED_DOCUMENTS);

        return scannedDocuments == null
            ? emptyList()
            : scannedDocuments.stream()
            .map(ScannedDocumentsHelper::createScannedDocumentWithCcdData)
            .collect(toList());
    }
}
