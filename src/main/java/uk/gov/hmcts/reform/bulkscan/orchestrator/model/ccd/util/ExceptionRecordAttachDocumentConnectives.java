package uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.util;

import java.util.Set;

public class ExceptionRecordAttachDocumentConnectives {

    /**
     * Exception Record documents already present in target case.
     */
    private Set<String> duplicates;

    /**
     * Exception Record documents which are still missing in target case.
     */
    private Set<String> missing;

    public ExceptionRecordAttachDocumentConnectives(
        Set<String> duplicates,
        Set<String> missing
    ) {
        this.duplicates = duplicates;
        this.missing = missing;
    }

    public boolean hasDuplicates() {
        return !duplicates.isEmpty() && !missing.isEmpty();
    }

    public boolean hasMissing() {
        return duplicates.isEmpty() && !missing.isEmpty();
    }

    public Set<String> getDuplicates() {
        return duplicates;
    }
}
