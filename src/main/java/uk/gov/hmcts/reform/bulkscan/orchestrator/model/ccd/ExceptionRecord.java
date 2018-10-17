package uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.List;

public class ExceptionRecord {

    @JsonProperty("journeyClassification")
    public final String classification;

    @JsonProperty("poBox")
    public final String poBox;

    @JsonProperty("poBoxJurisdiction")
    public final String jurisdiction;

    @JsonProperty("deliveryDate")
    public final LocalDateTime deliveryDate;

    @JsonProperty("openingDate")
    public final LocalDateTime openingDate;

    @JsonProperty("scannedDocuments")
    public final List<CcdCollectionElement<ScannedDocument>> scannedDocuments;

    public ExceptionRecord(
        String classification,
        String poBox,
        String jurisdiction,
        LocalDateTime deliveryDate,
        LocalDateTime openingDate,
        List<CcdCollectionElement<ScannedDocument>> scannedDocuments
    ) {
        this.classification = classification;
        this.poBox = poBox;
        this.jurisdiction = jurisdiction;
        this.deliveryDate = deliveryDate;
        this.openingDate = openingDate;
        this.scannedDocuments = scannedDocuments;
    }
}
