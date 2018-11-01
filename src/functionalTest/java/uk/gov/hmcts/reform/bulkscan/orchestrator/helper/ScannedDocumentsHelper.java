package uk.gov.hmcts.reform.bulkscan.orchestrator.helper;

import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.ScannedDocument;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toList;

@SuppressWarnings("unchecked")
public class ScannedDocumentsHelper {

    private ScannedDocumentsHelper() {
        // utility class
    }

    public static List<ScannedDocument> getScannedDocuments(CaseDetails caseDetails) {
        List<Map<String, Map<String, String>>> data =
            (List<Map<String, Map<String, String>>>) caseDetails.getData().get("scannedDocuments");

        return data.stream().map(ScannedDocumentsHelper::createDocumentFromMap).collect(toList());
    }

    private static ScannedDocument createDocumentFromMap(Map<String, Map<String, String>> object) {
        Map<String, String> doc = object.get("value");
        return new ScannedDocument(doc.get("fileName"),
            doc.get("controlNumber"),
            doc.get("type"),
            LocalDateTime.parse(doc.get("scannedDate")),
            null
        );
    }
}
