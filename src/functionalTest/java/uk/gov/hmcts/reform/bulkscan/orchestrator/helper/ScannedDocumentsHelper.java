package uk.gov.hmcts.reform.bulkscan.orchestrator.helper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.CcdDocument;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.ScannedDocument;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.document.domain.Document;
import uk.gov.hmcts.reform.document.domain.UploadResponse;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;

@SuppressWarnings("unchecked")
public class ScannedDocumentsHelper {
    private static final Logger log = LoggerFactory.getLogger(ScannedDocumentsHelper.class);

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
        log.info("DM url retried from ccd case {}", doc.get("url"));
        return new ScannedDocument(String.valueOf(doc.get("fileName")),
            String.valueOf(doc.get("controlNumber")),
            String.valueOf(doc.get("type")),
            LocalDateTime.parse((String) doc.get("scannedDate")),
            new CcdDocument(((HashMap<String, String>) doc.get("url")).getOrDefault("document_url", null))
        );
    }

    public static List<String> getScannedDocumentUrls(UploadResponse uploadResponse) {
        List<Document> documents = uploadResponse.getEmbedded().getDocuments();
        return documents.stream().map(document -> document.links.self.href).collect(Collectors.toList());
    }
}
