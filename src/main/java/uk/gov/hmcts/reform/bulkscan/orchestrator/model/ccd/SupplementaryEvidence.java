package uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd;

import java.util.List;

public class SupplementaryEvidence implements CaseData {

    // This field should always be set to null, as adding supplementary evidence
    // resets 'evidenceHandled' field in the case
    public final String evidenceHandled = null;

    public final List<CcdCollectionElement<ScannedDocument>> scannedDocuments;

    public SupplementaryEvidence(List<CcdCollectionElement<ScannedDocument>> scannedDocuments) {
        this.scannedDocuments = scannedDocuments;
    }
}
