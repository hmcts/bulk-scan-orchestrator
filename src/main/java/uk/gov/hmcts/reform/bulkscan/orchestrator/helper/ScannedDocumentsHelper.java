package uk.gov.hmcts.reform.bulkscan.orchestrator.helper;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.CcdCollectionElement;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.CcdKeyValue;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.ScannedDocument;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Document;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

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
        List<Map<String, Object>> data = (List<Map<String, Object>>) caseDetails.getData().get("scannedDocuments");

        return data.stream()
            .map(ScannedDocumentsHelper::createScannedDocumentWithCcdData)
            .map(ScannedDocumentsHelper::mapScannedDocument)
            .collect(toList());
    }

    public static List<CcdCollectionElement<CcdKeyValue>> mapOcrDataToCcdFormat(
        Map<String, String> ocrData
    ) {
        if (ocrData != null) {
            return ocrData
                .entrySet()
                .stream()
                .map(entry ->
                    new CcdCollectionElement<>(
                        new CcdKeyValue(entry.getKey(), entry.getValue())
                    )
                ).collect(toList());
        } else {
            return null;
        }
    }

    static ScannedDocument createScannedDocumentWithCcdData(Map<String, Object> object) {
        return objectMapper.convertValue(object.get("value"), ScannedDocument.class);
    }

    private static Document mapScannedDocument(ScannedDocument scannedDocument) {
        return new Document(
            scannedDocument.fileName,
            scannedDocument.controlNumber,
            scannedDocument.type,
            scannedDocument.scannedDate.toInstant(ZoneOffset.UTC),
            scannedDocument.url.documentUrl,
            mapOcrDataFromCcdFormat(scannedDocument.ocrData)
        );
    }

    private static Map<String, String> mapOcrDataFromCcdFormat(
        List<CcdCollectionElement<CcdKeyValue>> ocrData
    ) {
        if (ocrData != null) {
            return ocrData
                .stream()
                .map(element -> element.value)
                .collect(toMap(kv -> kv.key, kv -> kv.value));
        } else {
            return null;
        }
    }
}
