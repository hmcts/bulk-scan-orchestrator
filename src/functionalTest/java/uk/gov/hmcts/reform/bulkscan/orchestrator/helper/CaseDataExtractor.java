package uk.gov.hmcts.reform.bulkscan.orchestrator.helper;

import com.google.common.collect.ImmutableMap;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.CcdDocument;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.ScannedDocument;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Document;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

public class CaseDataExtractor {

    private CaseDataExtractor() {
        // utility class
    }

    @SuppressWarnings("unchecked")
    public static List<ScannedDocument> getScannedDocuments(CaseDetails caseDetails) {
        List<Map<String, Object>> data = (List<Map<String, Object>>) caseDetails.getData().get("scannedDocuments");

        return data.stream().map(ScannedDocumentsHelper::createScannedDocumentWithCcdData).collect(toList());
    }

    public static List<ScannedDocument> getScannedDocuments(Envelope envelope, String dmUrl, String contextPath) {
        List<Document> documents = envelope.documents;
        return documents.stream().map(document -> mapDocument(document, dmUrl, contextPath, envelope.deliveryDate)).collect(toList());
    }

    @SuppressWarnings("unchecked")
    public static Map<String, String> getOcrData(CaseDetails caseDetails) {
        List<Map<String, Object>> ccdOcrData =
            (List<Map<String, Object>>) caseDetails.getData().get("scanOCRData");

        if (ccdOcrData != null) {
            return ccdOcrData
                .stream()
                .map(ccdCollectionElement -> ((Map<String, String>) ccdCollectionElement.get("value")))
                .collect(
                    toMap(
                        map -> map.get("key"),
                        map -> map.get("value")
                    )
                );
        } else {
            return ImmutableMap.of();
        }
    }

    private static ScannedDocument mapDocument(Document document, String dmUrl, String contextPath, Instant deliveryDate) {
        return new ScannedDocument(
            document.fileName,
            document.controlNumber,
            document.type,
            document.subtype,
            ZonedDateTime.ofInstant(document.scannedAt, ZoneId.systemDefault()).toLocalDateTime(),
            new CcdDocument(String.join("/", dmUrl, contextPath, document.uuid)),
            ZonedDateTime.ofInstant(document.deliveryDate, ZoneId.systemDefault()).toLocalDateTime(),
            null
        );
    }
}
