package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import com.google.common.collect.ImmutableMap;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.util.function.Function;

public class TestCaseBuilder {
    private TestCaseBuilder(){
    }
    private static CaseDetails createCaseWithRef(Function<CaseDetails.CaseDetailsBuilder, CaseDetails.CaseDetailsBuilder> builder){
        return builder.apply(CaseDetails.builder()).build();
    }

    static CaseDetails caseWithReference(String caseReference) {
        return createCaseWithRef(b -> b.data(ImmutableMap.of("attachToCaseReference", caseReference)));
    }
}
