package uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.util;

import java.util.Set;

public class ExceptionRecordAttachDocumentConnectives {

    /**
     * Exception Record documents already present in target case.
     */
    private Set<String> existingInTargetCase;

    /**
     * Exception Record documents which are still missing in target case.
     */
    private Set<String> toBeAttachedToTargetCase;

    public ExceptionRecordAttachDocumentConnectives(
        Set<String> existingInTargetCase,
        Set<String> toBeAttachedToTargetCase
    ) {
        this.existingInTargetCase = existingInTargetCase;
        this.toBeAttachedToTargetCase = toBeAttachedToTargetCase;
    }

    public boolean hasDuplicatesAndMissing() {
        return !existingInTargetCase.isEmpty() && !toBeAttachedToTargetCase.isEmpty();
    }

    public boolean hasMissing() {
        return existingInTargetCase.isEmpty() && !toBeAttachedToTargetCase.isEmpty();
    }

    public Set<String> getExistingInTargetCase() {
        return existingInTargetCase;
    }

    public Set<String> getToBeAttachedToTargetCase() {
        return toBeAttachedToTargetCase;
    }
}
