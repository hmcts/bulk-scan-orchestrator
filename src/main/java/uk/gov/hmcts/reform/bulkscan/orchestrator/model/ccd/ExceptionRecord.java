package uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class ExceptionRecord implements CaseData {

    @JsonProperty("journeyClassification")
    public final String classification;

    public final String poBox;

    @JsonProperty("poBoxJurisdiction")
    public final String jurisdiction;

    // tmp
    public final LocalDate deliveryDate;

    // tmp
    public final LocalDate openingDate;

    public final List<CcdCollectionElement<ScannedDocument>> scanRecords;

    public ExceptionRecord(
        String classification,
        String poBox,
        String jurisdiction,
        LocalDateTime deliveryDate,
        LocalDateTime openingDate,
        List<CcdCollectionElement<ScannedDocument>> scannedRecords
    ) {
        this.classification = classification;
        this.poBox = poBox;
        this.jurisdiction = jurisdiction;
        this.deliveryDate = deliveryDate.toLocalDate();
        this.openingDate = openingDate.toLocalDate();
        this.scanRecords = scannedRecords;
    }
}
