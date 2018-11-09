package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import io.vavr.control.Validation;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.vavr.control.Validation.invalid;

class ScannedRecordValidator {
    private static final String SCAN_RECORDS = "scanRecords";

    @SuppressWarnings("unchecked")
    Validation<String, List<Map<String, Object>>> validate(CaseDetails theCase) {
        return getScannedRecord(theCase)
            .filter(list -> list instanceof List)
            .map(scanRecords -> (List<Map<String, Object>>) scanRecords)
            .filter(scanRecords -> !scanRecords.isEmpty())
            .map(Validation::<String, List<Map<String, Object>>>valid)
            .orElseGet(() -> invalid("There were no documents in exception record"));
    }

    private static Optional<Object> getScannedRecord(CaseDetails theCase) {
        return Optional.ofNullable(theCase)
            .map(CaseDetails::getData)
            .map(data -> data.get(SCAN_RECORDS));
    }
}
