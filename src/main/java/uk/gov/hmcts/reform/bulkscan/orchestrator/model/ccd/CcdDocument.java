package uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CcdDocument {

    @JsonProperty("document_url")
    public final String documentUrl;

    public CcdDocument(@JsonProperty("document_url") String documentUrl) {
        this.documentUrl = documentUrl;
    }
}
