package uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class SupplementaryEvidence {

    @JsonProperty("scannedDocuments")
    public final List<CcdCollectionElement<ScannedDocument>> scannedDocuments;

    public SupplementaryEvidence(List<CcdCollectionElement<ScannedDocument>> scannedDocuments) {
        this.scannedDocuments = scannedDocuments;
    }
}
