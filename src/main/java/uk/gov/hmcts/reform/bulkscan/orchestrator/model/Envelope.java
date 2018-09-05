package uk.gov.hmcts.reform.bulkscan.orchestrator.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Envelope {

    public final String id;
    public final String zipFileName;

    public Envelope(
        @JsonProperty("id") String id,
        @JsonProperty("zip_file_name") String zipFileName
    ) {
        this.id = id;
        this.zipFileName = zipFileName;
    }
}
