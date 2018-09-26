package uk.gov.hmcts.reform.bulkscan.orchestrator.services;

import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.ccd.client.CoreCaseDataApi;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

@Service
public class CcdCaseRetriever {
    public static final String CASE_TYPE_ID = "Bulk_Scanned";
    private CcdAuthService authenticator;
    private final CoreCaseDataApi coreCaseDataApi;


    CcdCaseRetriever(CcdAuthService authenticator, CoreCaseDataApi coreCaseDataApi) {
        this.authenticator = authenticator;
        this.coreCaseDataApi = coreCaseDataApi;
    }

    CaseDetails retrieve(String jurisdiction, String caseRef) {
        CcdAuthInfo info = authenticator.authenticateForJurisdiction(jurisdiction);
        return retrieveCase(jurisdiction, caseRef, info);
    }

    private CaseDetails retrieveCase(String jurisdiction, String caseRef, CcdAuthInfo info) {
        return coreCaseDataApi.readForCaseWorker(info.userToken,
            info.serviceToken,
            info.userDetails.getId(),
            jurisdiction,
            CASE_TYPE_ID,
            caseRef);
    }

}
