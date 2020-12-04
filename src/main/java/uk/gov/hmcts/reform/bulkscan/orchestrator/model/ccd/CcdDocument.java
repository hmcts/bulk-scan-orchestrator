package uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CcdDocument {

    @JsonProperty("document_url")
    public final String documentUrl;

    public final String documentFilename;

    public CcdDocument(@JsonProperty("document_url") String documentUrl,
                       @JsonProperty("document_filename") String documentFilename) {
        this.documentUrl = documentUrl;
        this.documentFilename = documentFilename;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CcdDocument)) {
            return false;
        }
        CcdDocument that = (CcdDocument) o;
        return Objects.equals(documentUrl, that.documentUrl)
            && Objects.equals(documentFilename, that.documentFilename);
    }

    @Override
    public int hashCode() {
        return Objects.hash(documentUrl, documentFilename);
    }
}
