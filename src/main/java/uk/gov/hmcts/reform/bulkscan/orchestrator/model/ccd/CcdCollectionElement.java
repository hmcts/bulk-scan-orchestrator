package uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd;


public class CcdCollectionElement<T> {

    public final T value;

    public CcdCollectionElement(T value) {
        this.value = value;
    }
}
