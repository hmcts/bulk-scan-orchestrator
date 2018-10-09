package uk.gov.hmcts.reform.bulkscan.orchestrator.helper;

import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Document;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toList;

public class TestHelper {

    public static void verifyCaseDetails(CaseDetails caseDetails) {
        List<Map<String, Map<String, String>>> data = (List<Map<String, Map<String, String>>>) caseDetails.getData().get("scannedDocuments");

        List<Document> documentList = data.stream().map(TestHelper::createDocument).collect(toList());
    }

    private static Document createDocument(Map<String, Map<String, String>> object) {
        Map<String, String> doc = object.get("value");
        Document document = new Document(String.valueOf(doc.get("file_name")),
            String.valueOf(doc.get("control_number")),
            String.valueOf(doc.get("type")),
            Instant.now(),
            String.valueOf(doc.get("url"))
        );
        return document;
    }
}
