package uk.gov.hmcts.reform.bulkscan.orchestrator.helper;

import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.CcdDocument;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.ScannedDocument;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Document;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toList;

@SuppressWarnings("unchecked")
public class ScannedDocumentsHelper {

    private ScannedDocumentsHelper() {
        // utility class
    }

    public static List<ScannedDocument> getScannedDocumentsForExceptionRecord(CaseDetails caseDetails) {
        List<Map<String, Object>> data = (List<Map<String, Object>>) caseDetails.getData().get("scanRecords");

        return data.stream().map(ScannedDocumentsHelper::createScannedDocumentWithCcdData).collect(toList());
    }

    public static List<ScannedDocument> getScannedDocumentsForSupplementaryEvidence(CaseDetails caseDetails) {
        List<Map<String, Object>> data = (List<Map<String, Object>>) caseDetails.getData().get("scannedDocuments");

        return data.stream().map(ScannedDocumentsHelper::createScannedDocumentWithCcdData).collect(toList());
    }

    public static List<ScannedDocument> getScannedDocuments(Envelope envelope) {
        List<Document> documents = envelope.documents;
        return documents.stream().map(ScannedDocumentsHelper::mapDocument).collect(toList());
    }

    private static ScannedDocument createScannedDocumentWithCcdData(Map<String, Object> object) {
        Map<String, Object> doc = (Map<String, Object>) object.get("value");
        return new ScannedDocument(String.valueOf(doc.get("fileName")),
            String.valueOf(doc.get("controlNumber")),
            String.valueOf(doc.get("type")),
            LocalDateTime.parse((String) doc.get("scannedDate")),
            new CcdDocument(
                ((HashMap<String, String>) doc.get("url"))
                    .getOrDefault("document_url", null)
            )
        );
    }

    private static ScannedDocument mapDocument(Document document) {
        return new ScannedDocument(document.fileName,
            document.controlNumber,
            document.type,
            document.scannedAt.atZone(ZoneId.systemDefault()).toLocalDateTime(),
            new CcdDocument(String.valueOf(document.url)));
    }
}
