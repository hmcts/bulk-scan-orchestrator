package uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd;

import com.fasterxml.jackson.annotation.JsonCreator;

public class CcdCollectionElement<T> {

    public final T value;

    @JsonCreator
    public CcdCollectionElement(T value) {
        this.value = value;
    }

}
