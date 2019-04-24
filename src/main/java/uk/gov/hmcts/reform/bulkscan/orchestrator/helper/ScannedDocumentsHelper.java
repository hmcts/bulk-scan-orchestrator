package uk.gov.hmcts.reform.bulkscan.orchestrator.helper;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.commons.lang3.StringUtils;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.ScannedDocument;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Document;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toList;

public class ScannedDocumentsHelper {

    private static final ObjectMapper objectMapper;

    static {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS);
    }

    private ScannedDocumentsHelper() {
        // utility class
    }

    @SuppressWarnings("unchecked")
    public static List<Document> getDocuments(CaseDetails caseDetails) {
        List<Map<String, Object>> scannedDocuments =
            (List<Map<String, Object>>) caseDetails.getData().get("scannedDocuments");

        if (scannedDocuments == null) {
            return Collections.emptyList();
        }

        return scannedDocuments.stream()
            .map(ScannedDocumentsHelper::createScannedDocumentWithCcdData)
            .map(ScannedDocumentsHelper::mapScannedDocument)
            .collect(toList());
    }

    static ScannedDocument createScannedDocumentWithCcdData(Map<String, Object> object) {
        return objectMapper.convertValue(object.get("value"), ScannedDocument.class);
    }

    private static Document mapScannedDocument(ScannedDocument doc) {
        if (doc == null) {
            return null;
        } else {
            return new Document(
                doc.fileName,
                doc.controlNumber,
                doc.type,
                doc.subtype,
                doc.scannedDate == null ? null : doc.scannedDate.atZone(ZoneId.systemDefault()).toInstant(),
                doc.url == null ? null : StringUtils.substringAfterLast(doc.url.documentUrl, "/"),
                doc.deliveryDate == null ? null : doc.deliveryDate.atZone(ZoneId.systemDefault()).toInstant()
            );
        }
    }
}
