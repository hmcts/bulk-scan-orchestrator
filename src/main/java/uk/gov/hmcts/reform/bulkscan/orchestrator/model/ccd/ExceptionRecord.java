package uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.List;

public class ExceptionRecord implements CaseData {

    @JsonProperty("journeyClassification")
    public final String classification;

    public final String poBox;

    @JsonProperty("poBoxJurisdiction")
    public final String jurisdiction;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'hh:mm:ss.SSS")
    public final LocalDateTime deliveryDate;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'hh:mm:ss.SSS")
    public final LocalDateTime openingDate;

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
        this.deliveryDate = deliveryDate;
        this.openingDate = openingDate;
        this.scanRecords = scannedRecords;
    }
}
