package uk.gov.hmcts.reform.bulkscan.orchestrator.config;

import java.util.List;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

public class ServiceConfigItem {
    @NotNull
    private String service;
    @NotNull
    private String jurisdiction;
    @NotEmpty
    private List<String> caseTypeIds;

    public String getService() {
        return service;
    }

    public void setService(String service) {
        this.service = service;
    }

    public String getJurisdiction() {
        return jurisdiction;
    }

    public void setJurisdiction(String jurisdiction) {
        this.jurisdiction = jurisdiction;
    }

    public List<String> getCaseTypeIds() {
        return caseTypeIds;
    }

    public void setCaseTypeIds(List<String> caseTypeIds) {
        this.caseTypeIds = caseTypeIds;
    }
}
