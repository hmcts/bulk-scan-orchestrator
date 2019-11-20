package uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.request;

public class OcrDataField {

    public final String name;
    public final String value;

    public OcrDataField(String name, String value) {
        this.name = name;
        this.value = value;
    }
}

