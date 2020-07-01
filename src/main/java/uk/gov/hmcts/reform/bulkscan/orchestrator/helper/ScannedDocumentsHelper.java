package uk.gov.hmcts.reform.bulkscan.orchestrator.helper;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.commons.lang.StringUtils;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.caseupdate.model.response.CaseUpdateDetails;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.request.ExceptionRecord;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.ScannedDocument;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Document;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;

public class ScannedDocumentsHelper {

    private static final ObjectMapper objectMapper;

    private static final String SCANNED_DOCUMENTS = "scannedDocuments";

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
            (List<Map<String, Object>>) caseDetails.getData().get(SCANNED_DOCUMENTS);

        if (scannedDocuments == null) {
            return emptyList();
        }

        return scannedDocuments.stream()
            .map(ScannedDocumentsHelper::createScannedDocumentWithCcdData)
            .map(ScannedDocumentsHelper::mapScannedDocument)
            .collect(toList());
    }

    public static void setExceptionRecordIdToScannedDocuments(
        ExceptionRecord exceptionRecord,
        CaseUpdateDetails caseDetails
    ) {
        List<String> dcns = exceptionRecord.scannedDocuments == null
            ? emptyList()
            : exceptionRecord.scannedDocuments
            .stream()
            .map(scannedDocument -> scannedDocument.controlNumber)
            .collect(toList());
        @SuppressWarnings("unchecked")
        Map<String, Object> caseData = (Map<String, Object>) caseDetails.caseData;
        List<ScannedDocument> scannedDocuments = getScannedDocuments(caseData);
        List<ScannedDocument> updatedScannedDocuments = scannedDocuments.stream()
            .map(scannedDocument ->
                dcns.contains(scannedDocument.controlNumber)
                    ? new ScannedDocument(
                    scannedDocument.fileName,
                    scannedDocument.controlNumber,
                    scannedDocument.type,
                    scannedDocument.subtype,
                    scannedDocument.scannedDate,
                    scannedDocument.url,
                    scannedDocument.deliveryDate,
                    exceptionRecord.id
                )
                    : scannedDocument
            )
            .collect(toList());
        caseData.put("scannedDocuments", updatedScannedDocuments);
    }

    @SuppressWarnings("unchecked")
    private static List<ScannedDocument> getScannedDocuments(Map<String, Object> caseData) {
        List<Map<String, Object>> scannedDocuments =
            (List<Map<String, Object>>) caseData.get("scannedDocuments");

        if (scannedDocuments == null) {
            return emptyList();
        }

        return scannedDocuments.stream()
            .map(ScannedDocumentsHelper::createScannedDocumentWithCcdData)
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
