package uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.List;

public class ExceptionRecord implements CaseData {

    @JsonProperty("journeyClassification")
    public final String classification;

    public final String poBox;

    @JsonProperty("poBoxJurisdiction")
    public final String jurisdiction;

    public final LocalDateTime deliveryDate;

    public final LocalDateTime openingDate;

    public final List<CcdCollectionElement<ScannedDocument>> scannedDocuments;

    @JsonProperty("scanOCRData")
    public final List<CcdCollectionElement<CcdKeyValue>> ocrData;

    public final List<CcdCollectionElement<String>> ocrDataValidationWarnings;

    public ExceptionRecord(
        String classification,
        String poBox,
        String jurisdiction,
        LocalDateTime deliveryDate,
        LocalDateTime openingDate,
        List<CcdCollectionElement<ScannedDocument>> scannedDocuments,
        List<CcdCollectionElement<CcdKeyValue>> ocrData,
        List<CcdCollectionElement<String>> ocrDataValidationWarnings
    ) {
        this.classification = classification;
        this.poBox = poBox;
        this.jurisdiction = jurisdiction;
        this.deliveryDate = deliveryDate;
        this.openingDate = openingDate;
        this.scannedDocuments = scannedDocuments;
        this.ocrData = ocrData;
        this.ocrDataValidationWarnings = ocrDataValidationWarnings;
    }
}
