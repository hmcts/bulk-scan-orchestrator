package uk.gov.hmcts.reform.bulkscan.orchestrator.config;

import java.util.List;

public class FormFieldMapping {
    private String formType;
    private List<String> ocrFields;

    public String getFormType() {
        return formType;
    }

    public void setFormType(String formType) {
        this.formType = formType;
    }

    public List<String> getOcrFields() {
        return ocrFields;
    }

    public void setOcrFields(List<String> ocrFields) {
        this.ocrFields = ocrFields;
    }
}
