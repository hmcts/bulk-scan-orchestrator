package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.callback;

import java.util.List;
import java.util.Map;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;

/**
 * Data model representing objects used in drafting a response to a callback request.
 */
public class ProcessResult {

    private final Map<String, Object> modifiedFields;

    private final List<String> warnings;

    private final List<String> errors;

    public ProcessResult(Map<String, Object> modifiedFields) {
        this.modifiedFields = modifiedFields;
        this.warnings = emptyList();
        this.errors = emptyList();
    }

    public ProcessResult(List<String> warnings, List<String> errors) {
        this.modifiedFields = emptyMap();
        this.warnings = warnings;
        this.errors = errors;
    }

    public Map<String, Object> getModifiedFields() {
        return modifiedFields;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public List<String> getErrors() {
        return errors;
    }
}
