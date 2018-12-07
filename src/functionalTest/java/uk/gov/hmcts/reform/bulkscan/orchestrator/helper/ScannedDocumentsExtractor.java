package uk.gov.hmcts.reform.bulkscan.orchestrator.helper;

import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.CcdDocument;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.ScannedDocument;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Document;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toList;

public class ScannedDocumentsExtractor {

    private ScannedDocumentsExtractor() {
        // utility class
    }

    @SuppressWarnings("unchecked")
    public static List<ScannedDocument> getScannedDocuments(CaseDetails caseDetails) {
        List<Map<String, Object>> data = (List<Map<String, Object>>) caseDetails.getData().get("scannedDocuments");

        return data.stream().map(ScannedDocumentsHelper::createScannedDocumentWithCcdData).collect(toList());
    }

    public static List<ScannedDocument> getScannedDocuments(Envelope envelope) {
        List<Document> documents = envelope.documents;
        return documents.stream().map(ScannedDocumentsExtractor::mapDocument).collect(toList());
    }

    private static ScannedDocument mapDocument(Document document) {
        return new ScannedDocument(
            document.fileName,
            document.controlNumber,
            document.type,
            document.scannedAt.atZone(ZoneId.systemDefault()).toLocalDateTime(),
            new CcdDocument(String.valueOf(document.url)),
            null
        );
    }
}
