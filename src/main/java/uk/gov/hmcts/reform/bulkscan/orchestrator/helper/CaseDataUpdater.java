package uk.gov.hmcts.reform.bulkscan.orchestrator.helper;

import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.cdam.CdamApiClient;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.CaseAction;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.CcdCollectionElement;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.CcdDocument;
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
    private final CdamApiClient cdamApiClient;

    public CaseDataUpdater(
        EnvelopeReferenceHelper envelopeReferenceHelper,
        CdamApiClient cdamApiClient
    ) {
        this.envelopeReferenceHelper = envelopeReferenceHelper;
        this.cdamApiClient = cdamApiClient;
    }

    public Map<String, Object> setExceptionRecordIdAndHashTokenToScannedDocuments(
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
                        return Map.of("value", new ScannedDocument(
                            doc.fileName,
                            doc.controlNumber,
                            doc.type,
                            doc.subtype,
                            doc.scannedDate,
                            new CcdDocument(doc.url.documentUrl,
                                cdamApiClient.getDocumentHash(
                                    exceptionRecord.poBoxJurisdiction,
                                    doc.url.documentUrl.substring(doc.url.documentUrl.lastIndexOf("/") + 1)
                                )
                            ),
                            doc.deliveryDate,
                            exceptionRecord.id
                        ));
                    } else {
                        // do not update the document if it was received with some previous exception record
                        return Map.of("value", doc);
                    }
                })
                .collect(toList());

        // replace scanned docs list
        updatedCaseData.put(SCANNED_DOCUMENTS, updatedScannedDocuments);

        return updatedCaseData;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> updateEnvelopeReferences(
        Map<String, Object> transformedCaseData,
        String envelopeId,
        CaseAction caseAction,
        Map<String, Object> existingCaseData
    ) {
        List<CcdCollectionElement<EnvelopeReference>> existingCaseRefs =
            envelopeReferenceHelper
                .parseEnvelopeReferences(
                    (List<Map<String, Object>>) existingCaseData.get(BULK_SCAN_ENVELOPES)
                );

        var updatedCaseRefs = newArrayList(existingCaseRefs);
        updatedCaseRefs.add(new CcdCollectionElement<>(new EnvelopeReference(envelopeId, caseAction)));

        var updatedCaseData = newHashMap(transformedCaseData);
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
