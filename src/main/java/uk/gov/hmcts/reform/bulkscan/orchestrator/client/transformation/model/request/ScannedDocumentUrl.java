package uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.request;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ScannedDocumentUrl {

    @JsonProperty("document_url")
    public final String documentUrl;

    @JsonProperty("document_filename")
    public final String documentFilename;

    @JsonProperty("document_binary_url")
    public final String documentBinaryUrl;

    public ScannedDocumentUrl(
        String documentUrl,
        String documentFilename,
        String documentBinaryUrl
    ) {
        this.documentUrl = documentUrl;
        this.documentFilename = documentFilename;
        this.documentBinaryUrl = documentBinaryUrl;
    }
}
