package uk.gov.hmcts.reform.bulkscan.orchestrator.helper;

import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CcdAuthenticator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CcdAuthenticatorFactory;
import uk.gov.hmcts.reform.ccd.client.CoreCaseDataApi;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.util.List;
import java.util.Map;

@Service
public class CaseSearcher {

    private final CcdAuthenticatorFactory factory;

    private final CoreCaseDataApi coreCaseDataApi;

    public CaseSearcher(CcdAuthenticatorFactory factory, CoreCaseDataApi coreCaseDataApi) {
        this.factory = factory;
        this.coreCaseDataApi = coreCaseDataApi;
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
