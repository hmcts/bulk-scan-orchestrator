package uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.request;

import com.fasterxml.jackson.annotation.JsonValue;

public enum DocumentType {
    CHERISHED("cherished"),
    COVERSHEET("coversheet"),
    FORM("form"),
    OTHER("other"),
    SUPPORTING_DOCUMENTS("supporting_documents"),
    WILL("will"),
    FORENSIC_SHEETS("forensic_sheets"),
    IHT("iht"),
    PPS_LEGAL_STATEMENT("pps_legal_statement");

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
