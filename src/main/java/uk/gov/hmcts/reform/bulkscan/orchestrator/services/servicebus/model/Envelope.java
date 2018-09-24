package uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class Envelope {

    public final String id;
    public final String caseRef;
    public final String jurisdiction;
    public final String zipFileName;
    public final Classification classification;
    public final List<String> docUrls;

    public Envelope(
        @JsonProperty(value = "id", required = true) String id,
        @JsonProperty(value = "case_ref", required = true) String caseRef,
        @JsonProperty(value = "jurisdiction", required = true) String jurisdiction,
        @JsonProperty(value = "zip_file_name", required = true) String zipFileName,
        @JsonProperty(value = "classification", required = true) Classification classification,
        @JsonProperty(value = "doc_urls", required = true) List<String> docUrls
    ) {
        this.id = id;
        this.caseRef = caseRef;
        this.jurisdiction = jurisdiction;
        this.zipFileName = zipFileName;
        this.classification = classification;
        this.docUrls = docUrls;
    }
}
