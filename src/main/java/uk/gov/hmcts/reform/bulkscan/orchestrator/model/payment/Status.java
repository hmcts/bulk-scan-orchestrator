package uk.gov.hmcts.reform.bulkscan.orchestrator.model.payment;

import com.fasterxml.jackson.annotation.JsonValue;

public enum Status {
    AWAITING("awaiting"),
    SUCCESS("success"),
    ERROR("error");

    private final String value;

    Status(String value) {
        this.value = value;
    }

    @Override
    @JsonValue
    public String toString() {
        return value;
    }
}
