package uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd;

import com.fasterxml.jackson.annotation.JsonProperty;

public class CCDDocument {

    @JsonProperty("document_url")
    public final String documentUrl;

    public CCDDocument(String documentUrl) {
        this.documentUrl = documentUrl;
    }
}
