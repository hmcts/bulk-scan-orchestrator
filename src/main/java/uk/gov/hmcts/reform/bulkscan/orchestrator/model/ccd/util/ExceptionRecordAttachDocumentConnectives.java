package uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.util;

import java.util.Set;

public class ExceptionRecordAttachDocumentConnectives {

    /**
     * Exception Record documents already present in target case.
     */
    private Set<String> duplicates;

    public ExceptionRecordAttachDocumentConnectives(
        Set<String> duplicates
    ) {
        this.duplicates = duplicates;
    }

    public boolean hasDuplicates() {
        return !duplicates.isEmpty();
    }

    public Set<String> getDuplicates() {
        return duplicates;
    }
}
