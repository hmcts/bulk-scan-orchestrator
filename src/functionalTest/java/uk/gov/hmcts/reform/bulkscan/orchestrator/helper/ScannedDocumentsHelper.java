package uk.gov.hmcts.reform.bulkscan.orchestrator.helper;

import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.CcdDocument;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.ScannedDocument;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.document.domain.UploadResponse;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toList;

@SuppressWarnings("unchecked")
public class ScannedDocumentsHelper {

    private ScannedDocumentsHelper() {
        // utility class
    }

    public static List<ScannedDocument> getScannedDocuments(CaseDetails caseDetails) {
        List<Map<String, Map<String, Object>>> data =
            (List<Map<String, Map<String, Object>>>) caseDetails.getData().get("scannedDocuments");

        return data.stream().map(ScannedDocumentsHelper::createDocumentFromMap).collect(toList());
    }

    private static ScannedDocument createDocumentFromMap(Map<String, Map<String, Object>> object) {
        Map<String, Object> doc = object.get("value");
        return new ScannedDocument(String.valueOf(doc.get("fileName")),
            String.valueOf(doc.get("controlNumber")),
            String.valueOf(doc.get("type")),
            LocalDateTime.parse((String) doc.get("scannedDate")),
            new CcdDocument(((HashMap<String, String>) doc.get("url")).get("document_url"))
        );
    }

    public static String getScannedDocumentUrl(UploadResponse uploadResponse) {
        List<uk.gov.hmcts.reform.document.domain.Document> documents = uploadResponse.getEmbedded().getDocuments();
        if (!documents.isEmpty() && documents.get(0).links != null) {
            return documents.get(0).links.self.href;
        }
        return null;
    }
}
