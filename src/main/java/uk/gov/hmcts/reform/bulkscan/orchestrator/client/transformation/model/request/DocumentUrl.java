package uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.request;

import com.fasterxml.jackson.annotation.JsonProperty;

public class DocumentUrl {

    @JsonProperty("document_url")
    public final String url;

    @JsonProperty("document_binary_url")
    public final String binaryUrl;

    @JsonProperty("document_filename")
    public final String filename;

    public DocumentUrl(
        String url,
        String binaryUrl,
        String filename
    ) {
        this.url = url;
        this.binaryUrl = binaryUrl;
        this.filename = filename;
    }
}
