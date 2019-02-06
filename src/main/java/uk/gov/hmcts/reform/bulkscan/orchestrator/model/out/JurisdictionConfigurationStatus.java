package uk.gov.hmcts.reform.bulkscan.orchestrator.model.out;

import com.fasterxml.jackson.annotation.JsonProperty;

public class JurisdictionConfigurationStatus {

    public final String jurisdiction;

    @JsonProperty("is_correct")
    public final boolean isCorrect;

    @JsonProperty("error_description")
    public final String errorDescription;

    public JurisdictionConfigurationStatus(
        String jurisdiction,
        boolean isCorrect,
        String errorDescription
    ) {
        this.jurisdiction = jurisdiction;
        this.isCorrect = isCorrect;
        this.errorDescription = errorDescription;
    }

    public JurisdictionConfigurationStatus(
        String jurisdiction,
        boolean isCorrect
    ) {
        this(jurisdiction, isCorrect, null);
    }
}
