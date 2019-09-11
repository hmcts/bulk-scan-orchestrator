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

    public final String formType;

    public final LocalDateTime deliveryDate;

    public final LocalDateTime openingDate;

    public final List<CcdCollectionElement<ScannedDocument>> scannedDocuments;

    @JsonProperty("scanOCRData")
    public final List<CcdCollectionElement<CcdKeyValue>> ocrData;


    public final List<CcdCollectionElement<String>> ocrDataValidationWarnings;

    // Yes/No field indicating if there are warnings to show
    public final String displayWarnings;

    @SuppressWarnings("squid:S00107") // number of params
    public ExceptionRecord(
        String classification,
        String poBox,
        String jurisdiction,
        String formType,
        LocalDateTime deliveryDate,
        LocalDateTime openingDate,
        List<CcdCollectionElement<ScannedDocument>> scannedDocuments,
        List<CcdCollectionElement<CcdKeyValue>> ocrData,
        List<CcdCollectionElement<String>> ocrDataValidationWarnings,
        String displayWarnings
    ) {
        this.classification = classification;
        this.poBox = poBox;
        this.jurisdiction = jurisdiction;
        this.formType = formType;
        this.deliveryDate = deliveryDate;
        this.openingDate = openingDate;
        this.scannedDocuments = scannedDocuments;
        this.ocrData = ocrData;
        this.ocrDataValidationWarnings = ocrDataValidationWarnings;
        this.displayWarnings = displayWarnings;
    }
}
