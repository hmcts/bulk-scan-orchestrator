package uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

public class CcdDocument {

    @JsonProperty("document_url")
    public final String documentUrl;

    public CcdDocument(String documentUrl) {
        this.documentUrl = documentUrl;
    }

    @Override
    public int hashCode() {
        return Objects.hash(documentUrl);
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
        return Objects.equals(documentUrl, that.documentUrl);
    }
}
