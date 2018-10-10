package uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd;

import com.fasterxml.jackson.annotation.JsonProperty;

public class CcdDocument {

    @JsonProperty("document_url")
    public final String documentUrl;

    public CcdDocument(String documentUrl) {
        this.documentUrl = documentUrl;
    }
}
