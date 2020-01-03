package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import java.util.List;

import static java.util.Collections.emptyList;

public class ErrorsAndWarnings {
    private final List<String> errors;
    private final List<String> warnings;

    private ErrorsAndWarnings(List<String> errors, List<String> warnings) {
        this.errors = errors;
        this.warnings = warnings;
    }

    static ErrorsAndWarnings withErrors(List<String> errors) {
        return new ErrorsAndWarnings(errors, emptyList());
    }

    static ErrorsAndWarnings withWarnings(List<String> warnings) {
        return new ErrorsAndWarnings(emptyList(), warnings);
    }

    public List<String> getErrors() {
        return errors;
    }

    public List<String> getWarnings() {
        return warnings;
    }
}
