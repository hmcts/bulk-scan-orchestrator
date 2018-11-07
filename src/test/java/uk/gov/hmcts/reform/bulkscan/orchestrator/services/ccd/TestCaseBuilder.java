package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails.CaseDetailsBuilder;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

class TestCaseBuilder {
    private TestCaseBuilder(){
    }

    static CaseDetails createCaseWith(Function<CaseDetailsBuilder, CaseDetailsBuilder> builder) {
        return builder.apply(CaseDetails.builder()).build();
    }

    static CaseDetails caseWithReference(String caseReference) {
        Map<String, Object> data = new HashMap<>();
        data.put("attachToCaseReference", caseReference);
        return createCaseWith(b -> b.data(data));
    }
}
