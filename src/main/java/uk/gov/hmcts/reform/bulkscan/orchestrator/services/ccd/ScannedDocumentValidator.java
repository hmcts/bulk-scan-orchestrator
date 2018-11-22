package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import io.vavr.control.Validation;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.vavr.control.Validation.invalid;

class ScannedDocumentValidator {
    private static final String SCANNED_DOCUMENTS = "scannedDocuments";

    @SuppressWarnings("unchecked")
    Validation<String, List<Map<String, Object>>> validate(CaseDetails theCase) {
        return getScannedRecord(theCase)
            .filter(list -> list instanceof List)
            .map(scannedDocuments -> (List<Map<String, Object>>) scannedDocuments)
            .filter(scannedDocuments -> !scannedDocuments.isEmpty())
            .map(Validation::<String, List<Map<String, Object>>>valid)
            .orElseGet(() -> invalid("There were no documents in exception record"));
    }

    private static Optional<Object> getScannedRecord(CaseDetails theCase) {
        return Optional.ofNullable(theCase)
            .map(CaseDetails::getData)
            .map(data -> data.get(SCANNED_DOCUMENTS));
    }
}
