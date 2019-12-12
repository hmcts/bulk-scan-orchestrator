package uk.gov.hmcts.reform.bulkscan.orchestrator.helper;

import com.google.common.collect.ImmutableMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CcdAuthenticator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CcdAuthenticatorFactory;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.events.CreateExceptionRecord;
import uk.gov.hmcts.reform.ccd.client.CoreCaseDataApi;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.util.List;
import java.util.Map;

@Service
public class CaseSearcher {

    private final CcdAuthenticatorFactory factory;

    private final CoreCaseDataApi coreCaseDataApi;
    private static final Logger log = LoggerFactory.getLogger(CaseSearcher.class);

    public CaseSearcher(CcdAuthenticatorFactory factory, CoreCaseDataApi coreCaseDataApi) {
        this.factory = factory;
        this.coreCaseDataApi = coreCaseDataApi;
    }

    public List<CaseDetails> findExceptionRecordByPoBox(String jurisdiction, String poBox) {
        return search(
            jurisdiction,
            jurisdiction + "_" + CreateExceptionRecord.CASE_TYPE,
            ImmutableMap.of("case.poBox", poBox)
        );
    }

    public List<CaseDetails> search(
        String jurisdiction,
        String caseTypeId,
        Map<String, String> searchCriteria
    ) {
        // not including in try catch to fast fail the method
        CcdAuthenticator authenticator = factory.createForJurisdiction(jurisdiction);

        return coreCaseDataApi.searchForCaseworker(
            authenticator.getUserToken(),
            authenticator.getServiceToken(),
            authenticator.getUserDetails().getId(),
            jurisdiction,
            caseTypeId,
            searchCriteria
        );
    }
}
