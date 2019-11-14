package uk.gov.hmcts.reform.bulkscan.orchestrator.config;

public class FormFieldMapping {
    private String formType;
    private String ocrField;

    public String getFormType() {
        return formType;
    }

    public void setFormType(String formType) {
        this.formType = formType;
    }

    public String getOcrField() {
        return ocrField;
    }

    public void setOcrField(String ocrField) {
        this.ocrField = ocrField;
    }
}
