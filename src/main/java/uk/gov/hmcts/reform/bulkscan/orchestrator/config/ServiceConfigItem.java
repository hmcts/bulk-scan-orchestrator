package uk.gov.hmcts.reform.bulkscan.orchestrator.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotNull;

import static java.util.Collections.emptyList;
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

    /**
     * URL to an endpoint that handles update existing case upon exception record to a service specific case.
     */
    private String updateUrl;

    private List<String> caseTypeIds;

    private boolean allowCreatingCaseBeforePaymentsAreProcessed = false;

    private List<Classification> allowAttachingToCaseBeforePaymentsAreProcessedForClassifications = emptyList();

    private Map<String, List<String>> formTypeToSurnameOcrFieldMappings = new HashMap<>();

    private boolean autoCaseCreationEnabled = false;
    private boolean autoCaseUpdateEnabled = false;
    private boolean caseDefinitionHasEnvelopeIds = false;
    private boolean searchCasesByEnvelopeId = false;

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

    public String getUpdateUrl() {
        return updateUrl;
    }

    public void setUpdateUrl(String updateUrl) {
        this.updateUrl = updateUrl;
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

    public List<Classification> getAllowAttachingToCaseBeforePaymentsAreProcessedForClassifications() {
        return allowAttachingToCaseBeforePaymentsAreProcessedForClassifications;
    }

    public void setAllowAttachingToCaseBeforePaymentsAreProcessedForClassifications(
        List<Classification> allowAttachingToCaseBeforePaymentsAreProcessedForClassifications
    ) {
        this.allowAttachingToCaseBeforePaymentsAreProcessedForClassifications
            = allowAttachingToCaseBeforePaymentsAreProcessedForClassifications;
    }

    public List<String> getSurnameOcrFieldNameList(String formType) {
        return formTypeToSurnameOcrFieldMappings.getOrDefault(formType, emptyList());
    }

    public void setFormTypeToSurnameOcrFieldMappings(List<FormFieldMapping> formTypeToSurnameOcrFieldMappings) {
        this.formTypeToSurnameOcrFieldMappings = formTypeToSurnameOcrFieldMappings.stream()
            .collect(
                toMap(
                    FormFieldMapping::getFormType,
                    FormFieldMapping::getOcrFields,
                    (v1, v2) -> {
                        throw new InvalidConfigurationException(
                            String.format("Form type has multiple mappings to surname fields %s, %s.", v1, v2)
                        );
                    }
                )
            );
    }

    public boolean getAutoCaseCreationEnabled() {
        return autoCaseCreationEnabled;
    }

    public void setAutoCaseCreationEnabled(boolean autoCaseCreationEnabled) {
        this.autoCaseCreationEnabled = autoCaseCreationEnabled;
    }

    public boolean getAutoCaseUpdateEnabled() {
        return autoCaseUpdateEnabled;
    }

    public void setAutoCaseUpdateEnabled(boolean autoCaseUpdateEnabled) {
        this.autoCaseUpdateEnabled = autoCaseUpdateEnabled;
    }

    public boolean getCaseDefinitionHasEnvelopeIds() {
        return caseDefinitionHasEnvelopeIds;
    }

    public void setCaseDefinitionHasEnvelopeIds(boolean caseDefinitionHasEnvelopeIds) {
        this.caseDefinitionHasEnvelopeIds = caseDefinitionHasEnvelopeIds;
    }

    public boolean getSearchCasesByEnvelopeId() {
        return searchCasesByEnvelopeId;
    }

    public void setSearchCasesByEnvelopeId(boolean value) {
        this.searchCasesByEnvelopeId = value;
    }

    // endregion
}
