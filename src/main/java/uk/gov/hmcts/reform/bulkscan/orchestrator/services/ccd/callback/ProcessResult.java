package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.callback;

import java.util.List;
import java.util.Map;

/**
 * Data model representing objects used in drafting a response to a callback request.
 */
public class ProcessResult {

    private final Map<String, Object> modifiedFields;

    private final List<String> warnings;

    public ProcessResult(
        Map<String, Object> modifiedFields,
        List<String> warnings
    ) {
        this.modifiedFields = modifiedFields;
        this.warnings = warnings;
    }

    public Map<String, Object> getModifiedFields() {
        return modifiedFields;
    }

    public List<String> getWarnings() {
        return warnings;
    }
}
