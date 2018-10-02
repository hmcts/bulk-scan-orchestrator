package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.ccd.client.CoreCaseDataApi;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

@Service
public class CaseRetriever {
    public static final String CASE_TYPE_ID = "Bulk_Scanned";

    private final CcdAuthenticatorFactory factory;
    private final CoreCaseDataApi coreCaseDataApi;

    CaseRetriever(CcdAuthenticatorFactory factory, CoreCaseDataApi coreCaseDataApi) {
        this.factory = factory;
        this.coreCaseDataApi = coreCaseDataApi;
    }

    public CaseDetails retrieve(String jurisdiction, String caseRef) {
        CcdAuthenticator info = factory.createForJurisdiction(jurisdiction);
        return retrieveCase(jurisdiction, caseRef, info);
    }

    private CaseDetails retrieveCase(String jurisdiction, String caseRef, CcdAuthenticator authenticator) {
        return coreCaseDataApi.readForCaseWorker(
            authenticator.getUserToken(),
            authenticator.getServiceToken(),
            authenticator.getUserDetails().getId(),
            jurisdiction,
            CASE_TYPE_ID,
            caseRef
        );
    }

}
