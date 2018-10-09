package uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd;


import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class SupplementaryEvidence {

    @JsonProperty("scannedDocuments")
    public final List<CCDCollectionElement<ScannedDocument>> scannedDocuments;

    public SupplementaryEvidence(List<CCDCollectionElement<ScannedDocument>> scannedDocuments) {
        this.scannedDocuments = scannedDocuments;
    }
}
