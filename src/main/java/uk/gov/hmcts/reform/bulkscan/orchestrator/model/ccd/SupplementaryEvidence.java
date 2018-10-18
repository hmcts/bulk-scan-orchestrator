package uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd;

import java.util.List;

public class SupplementaryEvidence implements CaseData {

    // this field should always null in JSON sent to CCD
    public final String evidenceHandled = null;

    public final List<CcdCollectionElement<ScannedDocument>> scannedDocuments;

    public SupplementaryEvidence(List<CcdCollectionElement<ScannedDocument>> scannedDocuments) {
        this.scannedDocuments = scannedDocuments;
    }
}
