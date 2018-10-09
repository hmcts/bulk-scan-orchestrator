package uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd;


public class CCDCollectionElement<T> {

    public final T value;

    public CCDCollectionElement(T value) {
        this.value = value;
    }
}
