package uk.gov.hmcts.reform.bulkscan.orchestrator.helper;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.util.Assert;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.ScannedDocument;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Document;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.time.ZoneId;
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
        Map<String, Object> caseData = caseDetails.getData();
        Assert.notNull(caseData, "Case Data must not be null");

        List<Map<String, Object>> scannedDocuments = (List<Map<String, Object>>) caseData.get("scannedDocuments");
        // ccd returns an empty list when scanned documents are not present.
        Assert.notNull(scannedDocuments, "Scanned Documents must not be null");

        return scannedDocuments.stream()
            .map(ScannedDocumentsHelper::createScannedDocumentWithCcdData)
            .map(ScannedDocumentsHelper::mapScannedDocument)
            .collect(toList());
    }

    static ScannedDocument createScannedDocumentWithCcdData(Map<String, Object> object) {
        return objectMapper.convertValue(object.get("value"), ScannedDocument.class);
    }

    private static Document mapScannedDocument(ScannedDocument scannedDocument) {
        return new Document(
            scannedDocument.fileName,
            scannedDocument.controlNumber,
            scannedDocument.type,
            scannedDocument.scannedDate.atZone(ZoneId.systemDefault()).toInstant(),
            scannedDocument.url.documentUrl
        );
    }
}
