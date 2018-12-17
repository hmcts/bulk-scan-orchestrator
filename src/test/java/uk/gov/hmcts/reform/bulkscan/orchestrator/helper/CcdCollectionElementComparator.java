package uk.gov.hmcts.reform.bulkscan.orchestrator.helper;

import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.CcdCollectionElement;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.CcdKeyValue;

import java.util.Comparator;

public class CcdCollectionElementComparator implements Comparator<CcdCollectionElement<CcdKeyValue>> {

    @Override
    public int compare(CcdCollectionElement<CcdKeyValue> o1, CcdCollectionElement<CcdKeyValue> o2) {
        return o1.value.key.compareTo(o2.value.key) & o1.value.value.compareTo(o2.value.value);
    }
}
