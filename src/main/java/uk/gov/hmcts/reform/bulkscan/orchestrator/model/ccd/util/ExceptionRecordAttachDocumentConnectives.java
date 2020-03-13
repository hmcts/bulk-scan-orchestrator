package uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.util;

import java.util.Set;

public class ExceptionRecordAttachDocumentConnectives {

    /**
     * Exception Record documents already present in target case.
     */
    private Set<String> existingInTargetCase;

    /**
     * Number of Exception Record documents.
     */
    private int exceptionRecordDocSize;

    public ExceptionRecordAttachDocumentConnectives(
        Set<String> existingInTargetCase,
        int exceptionRecordDocSize
    ) {
        this.existingInTargetCase = existingInTargetCase;
        this.exceptionRecordDocSize = exceptionRecordDocSize;
    }

    public boolean hasDuplicatesAndMissing() {
        return !existingInTargetCase.isEmpty() && exceptionRecordDocSize != existingInTargetCase.size();
    }

    public boolean hasMissing() {
        return existingInTargetCase.isEmpty() && exceptionRecordDocSize > 0;
    }

    public Set<String> getExistingInTargetCase() {
        return existingInTargetCase;
    }
}
