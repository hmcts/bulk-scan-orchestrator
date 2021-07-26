package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class CallbackValidations {
    private CallbackValidations() {
    }


    @SuppressWarnings("unchecked")
    public static Optional<List<Map<String, Object>>> getOcrData(CaseDetails theCase) {
        return Optional.ofNullable(theCase)
            .map(CaseDetails::getData)
            .map(data -> (List<Map<String, Object>>) data.get("scanOCRData"));
    }
}
