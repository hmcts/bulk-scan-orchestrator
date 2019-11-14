package uk.gov.hmcts.reform.bulkscan.orchestrator.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotNull;

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

    private Map<String, String> formTypeToSurnameOcrFieldMappings = new HashMap<>();

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

    public String getSurnameOcrFieldName(String formType) {
        return formTypeToSurnameOcrFieldMappings.get(formType);
    }

    public void setFormTypeToSurnameOcrFieldMappings(List<FormFieldMapping> formTypeToSurnameOcrFieldMappings) {
        this.formTypeToSurnameOcrFieldMappings = formTypeToSurnameOcrFieldMappings.stream()
            .collect(
                toMap(
                    FormFieldMapping::getFormType,
                    FormFieldMapping::getOcrField,
                    (v1, v2) -> {
                        throw new InvalidConfigurationException(
                            String.format("Form type has multiple mappings to surname fields %s, %s.", v1, v2)
                        );
                    }
                )
            );
//                HashMap::new,
//                (map, mapping) -> {
//                    String formType = mapping.getFormType();
//                    if (map.containsKey(formType)) {
//                        String msg = String.format("Form type %s has multiple mappings to surname fields.", formType);
//                        log.error(msg);
//                        throw new InvalidConfigurationException(msg);
//                    } else {
//                        map.put(formType, mapping.getOcrField());
//                    }
//                },
//                HashMap::putAll
//            );
    }

    // endregion
}
