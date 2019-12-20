package uk.gov.hmcts.reform.bulkscan.orchestrator.config;

import java.util.List;

public class FormFieldMapping {
    private String formType;
    private List<String> ocrFieldList;

    public String getFormType() {
        return formType;
    }

    public void setFormType(String formType) {
        this.formType = formType;
    }

    public List<String> getOcrFieldList() {
        return ocrFieldList;
    }

    public void setOcrFieldList(List<String> ocrField) {
        this.ocrFieldList = ocrField;
    }
}
