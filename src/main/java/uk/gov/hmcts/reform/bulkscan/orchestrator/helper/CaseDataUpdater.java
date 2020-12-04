package uk.gov.hmcts.reform.bulkscan.orchestrator.helper;

import com.google.common.collect.ImmutableMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(CaseDataUpdater.class);

    private final EnvelopeReferenceHelper envelopeReferenceHelper;

    public CaseDataUpdater(EnvelopeReferenceHelper envelopeReferenceHelper) {
        this.envelopeReferenceHelper = envelopeReferenceHelper;
    }

    public Map<String, Object> setExceptionRecordIdToScannedDocuments(
        ExceptionRecord exceptionRecord,
        Map<String, Object> caseData
    ) {
        var updatedCaseData = newHashMap(caseData);

        List<String> exceptionRecordDcns =
            exceptionRecord
                .scannedDocuments
                .stream()
                .map(doc -> doc.controlNumber)
                .collect(toList());

        List<Map<String, ScannedDocument>> updatedScannedDocuments =
            getScannedDocuments(caseData)
                .stream()
                .map(doc -> {
                    if (exceptionRecordDcns.contains(doc.controlNumber)) {
                        // set exceptionReference if the document received with the exception record
                        return ImmutableMap.of("value", new ScannedDocument(
                            doc.fileName,
                            doc.controlNumber,
                            doc.type,
                            doc.subtype,
                            doc.scannedDate,
                            doc.url,
                            doc.deliveryDate,
                            exceptionRecord.id
                        ));
                    } else {
                        // do not update the document if it was received with some previous exception record
                        return ImmutableMap.of("value", doc);
                    }
                })
                .collect(toList());

        // replace scanned docs list
        updatedCaseData.put(SCANNED_DOCUMENTS, updatedScannedDocuments);

        return updatedCaseData;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> updateEnvelopeReferences(
        Map<String, Object> caseData,
        String envelopeId,
        CaseAction caseAction
    ) {
        List<CcdCollectionElement<EnvelopeReference>> existingCaseRefs =
            envelopeReferenceHelper
                .parseEnvelopeReferences(
                    (List<Map<String, Object>>) caseData.get(BULK_SCAN_ENVELOPES)
                );


        log.info(
            "Existing bulkscanenvelope ref {}",
            existingCaseRefs
                .stream()
                .map(r -> "(" + r.value.id + " - " + r.value.action + ")")
                .reduce("", (r1, r2) -> r1 + " " + r2),
            envelopeId
        );

        var updatedCaseRefs = newArrayList(existingCaseRefs);
        updatedCaseRefs.add(new CcdCollectionElement<>(new EnvelopeReference(envelopeId, caseAction)));
        log.info(
            "Updated  bulkscanenvelope ref {}",
            updatedCaseRefs
                .stream()
                .map(r -> "(" + r.value.id + " - " + r.value.action + ")")
                .reduce("", (r1, r2) -> r1 + " " + r2),
            envelopeId
        );

        var updatedCaseData = newHashMap(caseData);
        updatedCaseData.put(BULK_SCAN_ENVELOPES, updatedCaseRefs);

        return updatedCaseData;
    }

    @SuppressWarnings("unchecked")
    private List<ScannedDocument> getScannedDocuments(Map<String, Object> caseData) {
        var scannedDocuments = (List<Map<String, Object>>) caseData.get(SCANNED_DOCUMENTS);

        return scannedDocuments == null
            ? emptyList()
            : scannedDocuments.stream()
            .map(ScannedDocumentsHelper::createScannedDocumentWithCcdData)
            .collect(toList());
    }
}
