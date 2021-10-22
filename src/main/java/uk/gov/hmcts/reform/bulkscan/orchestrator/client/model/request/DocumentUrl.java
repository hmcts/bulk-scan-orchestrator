package uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class DocumentUrl {

    @JsonProperty("document_url")
    public final String url;

    @JsonProperty("document_hash")
    public final String documentHash;

    @JsonProperty("document_binary_url")
    public final String binaryUrl;

    @JsonProperty("document_filename")
    public final String filename;

    public DocumentUrl(
            String url,
            String documentHash,
            String binaryUrl,
            String filename
    ) {
        this.url = url;
        this.documentHash = documentHash;
        this.binaryUrl = binaryUrl;
        this.filename = filename;
    }
}
