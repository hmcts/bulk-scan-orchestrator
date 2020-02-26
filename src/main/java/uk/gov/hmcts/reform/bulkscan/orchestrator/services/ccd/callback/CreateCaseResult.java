package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.callback;

import java.util.List;

import static java.util.Collections.emptyList;

/**
 * Data model representing objects used in drafting a response to a callback request.
 */
public class CreateCaseResult {

    public final Long caseId;

    public final List<String> warnings;

    public final List<String> errors;

    public CreateCaseResult(long caseId) {
        this.caseId = caseId;
        this.warnings = emptyList();
        this.errors = emptyList();
    }

    public CreateCaseResult(List<String> warnings, List<String> errors) {
        this.caseId = null;
        this.warnings = warnings;
        this.errors = errors;
    }
}
