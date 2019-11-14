package uk.gov.hmcts.reform.bulkscan.orchestrator.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotNull;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

public class ServiceConfigItem {

    private static final Logger log = LoggerFactory.getLogger(ServiceConfigItem.class);

    @NotNull
    private String service;

    @NotNull
    private String jurisdiction;

    /**
     * URL to an endpoint that handles transforming exception record to a service specific case.
     */
    private String transformationUrl;

    private List<String> caseTypeIds;

    private boolean allowCreatingCaseBeforePaymentsAreProcessed = false;

    private Map<String, String> surnameMappings;

    // region getters & setters

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

    public String getTransformationUrl() {
        return transformationUrl;
    }

    public void setTransformationUrl(String transformationUrl) {
        this.transformationUrl = transformationUrl;
    }

    public List<String> getCaseTypeIds() {
        return caseTypeIds;
    }

    public void setCaseTypeIds(List<String> caseTypeIds) {
        this.caseTypeIds = caseTypeIds;
    }

    public boolean allowCreatingCaseBeforePaymentsAreProcessed() {
        return allowCreatingCaseBeforePaymentsAreProcessed;
    }

    public void setAllowCreatingCaseBeforePaymentsAreProcessed(boolean allowCreatingCaseBeforePaymentsAreProcessed) {
        this.allowCreatingCaseBeforePaymentsAreProcessed = allowCreatingCaseBeforePaymentsAreProcessed;
    }

    public String getSurnameMapping(String formType) {
        return surnameMappings.get(formType);
    }

    public void setSurnameMappings(List<FormFieldMapping> surnameMappings) {
        this.surnameMappings = surnameMappings.stream()
            .collect(groupingBy(FormFieldMapping::getFormType, toList()))
            .entrySet().stream()
            .collect(toMap(
                e -> e.getKey(),
                e -> {
                    if (e.getValue().size() > 1) {
                        log.error("Form type {} has {} mappings to surname fields",
                            e.getKey(),
                            e.getValue().size());
                    }
                    return e.getValue().get(0).getOcrField();
                }
            ));
    }

    // endregion
}
