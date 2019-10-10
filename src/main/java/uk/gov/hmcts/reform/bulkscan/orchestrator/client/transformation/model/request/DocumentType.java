package uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.request;

import com.fasterxml.jackson.annotation.JsonValue;

public enum DocumentType {
    CHERISHED("cherished"),
    COVERSHEET("coversheet"),
    FORM("form"),
    OTHER("other");

    private final String value;

    DocumentType(String value) {
        this.value = value;
    }

    @Override
    @JsonValue
    public String toString() {
        return value;
    }
}
