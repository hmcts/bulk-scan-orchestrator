package uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CcdDocument {

    @JsonProperty("document_url")
    public final String documentUrl;

    @JsonProperty("document_hash")
    public final String documentHash;

    public CcdDocument(
            @JsonProperty("document_url") String documentUrl,
            @JsonProperty("document_hash") String documentHash
    ) {
        this.documentUrl = documentUrl;
        this.documentHash = documentHash;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        CcdDocument that = (CcdDocument) o;
        return Objects.equals(documentUrl, that.documentUrl) && Objects.equals(documentHash, that.documentHash);
    }

    @Override
    public int hashCode() {
        return Objects.hash(documentUrl, documentHash);
    }
}
