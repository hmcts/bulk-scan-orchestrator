package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.ccd.client.CoreCaseDataApi;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

@Service
public class CcdCaseRetriever {
    public static final String CASE_TYPE_ID = "Bulk_Scanned";

    private final CcdAuthenticatorFactory factory;
    private final CoreCaseDataApi coreCaseDataApi;

    CcdCaseRetriever(CcdAuthenticatorFactory factory, CoreCaseDataApi coreCaseDataApi) {
        this.factory = factory;
        this.coreCaseDataApi = coreCaseDataApi;
    }

    public CaseDetails retrieve(String jurisdiction, String caseRef) {
        Authenticator info = factory.createForJurisdiction(jurisdiction);
        return retrieveCase(jurisdiction, caseRef, info);
    }

    private CaseDetails retrieveCase(String jurisdiction, String caseRef, Authenticator authenticator) {
        return coreCaseDataApi.readForCaseWorker(
            authenticator.getUserToken(),
            authenticator.getServiceToken(),
            authenticator.userDetails.getId(),
            jurisdiction,
            CASE_TYPE_ID,
            caseRef
        );
    }

}
