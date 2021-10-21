package uk.gov.hmcts.reform.bulkscan.orchestrator.client.cdam;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class GetDocumentHashResponse {

    public final String hashToken;

    public GetDocumentHashResponse(@JsonProperty("hashToken") String hashToken) {
        this.hashToken = hashToken;
    }
}
