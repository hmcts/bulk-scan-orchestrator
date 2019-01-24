package uk.gov.hmcts.reform.bulkscan.orchestrator.helper;

import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.CcdCollectionElement;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.CcdKeyValue;

import java.util.Comparator;

public class CcdCollectionElementComparator implements Comparator<CcdCollectionElement<CcdKeyValue>> {

    @Override
    public int compare(CcdCollectionElement<CcdKeyValue> o1, CcdCollectionElement<CcdKeyValue> o2) {
        return o1.value.key.compareTo(o2.value.key) & compareValues(o1.value, o2.value);
    }

    private int compareValues(CcdKeyValue o1, CcdKeyValue o2) {
        if (o1.value == null && o2.value == null) {
            return 0;
        }
        return o1.value.compareTo(o2.value);
    }

}
