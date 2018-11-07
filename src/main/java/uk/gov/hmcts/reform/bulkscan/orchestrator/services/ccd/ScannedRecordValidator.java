package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import io.vavr.control.Option;
import io.vavr.control.Validation;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.util.List;
import java.util.Map;

import static io.vavr.control.Validation.invalid;

class ScannedRecordValidator {
    private static final String SCAN_RECORDS = "scanRecords";


    @SuppressWarnings("unchecked")
    Validation<String, List<Map<String, Object>>> validate(CaseDetails theCase) {
        return getScannedRecord(theCase)
            .flatMap(data -> Option.of(data)
                .filter(list -> list instanceof List)
                .map(scanRecords -> (List<Map<String, Object>>) scanRecords))
            .filter(scanRecords -> scanRecords.size() > 0)
            .map(Validation::<String, List<Map<String, Object>>>valid)
            .getOrElse(() -> invalid("InternalError: no scanned document supplied"));
    }

    private static Option<Object> getScannedRecord(CaseDetails theCase) {
        return Option.of(theCase)
            .flatMap(c -> Option.of(c.getData()))
            .flatMap(data -> Option.of(data.get(SCAN_RECORDS)));
    }
}
