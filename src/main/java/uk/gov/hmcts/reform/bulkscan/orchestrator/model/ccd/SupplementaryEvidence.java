package uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

public class SupplementaryEvidence {

    // This field should always be set to false aka YesOrNo
    // as adding supplementary evidence resets 'evidenceHandled' field in the case
    @SuppressWarnings("squid:S1170") // this field shouldn't be made static
    public final String evidenceHandled = "No";

    public final List<CcdCollectionElement<ScannedDocument>> scannedDocuments;

    // as of now, not every case definition contains this field - can't always include it
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public final List<CcdCollectionElement<EnvelopeReference>> bulkScanEnvelopes;

    public SupplementaryEvidence(
        List<CcdCollectionElement<ScannedDocument>> scannedDocuments,
        List<CcdCollectionElement<EnvelopeReference>> bulkScanEnvelopes
    ) {
        this.scannedDocuments = scannedDocuments;
        this.bulkScanEnvelopes = bulkScanEnvelopes;
    }
}
