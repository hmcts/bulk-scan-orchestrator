package uk.gov.hmcts.reform.bulkscan.orchestrator.services;

import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.ccd.client.CoreCaseDataApi;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

@Service
public class CcdCaseRetriever {
    public static final String CASE_TYPE_ID = "Bulk_Scanned";

    private CcdAuthenticatorFactory authenticator;
    private final CoreCaseDataApi coreCaseDataApi;

    CcdCaseRetriever(CcdAuthenticatorFactory authService, CoreCaseDataApi coreCaseDataApi) {
        this.authenticator = authService;
        this.coreCaseDataApi = coreCaseDataApi;
    }

    CaseDetails retrieve(String jurisdiction, String caseRef) {
        Authenticator info = authenticator.createForJurisdiction(jurisdiction);
        return retrieveCase(jurisdiction, caseRef, info);
    }

    private CaseDetails retrieveCase(String jurisdiction, String caseRef, Authenticator info) {
        return coreCaseDataApi.readForCaseWorker(
            info.getUserToken(),
            info.getServiceToken(),
            info.userDetails.getId(),
            jurisdiction,
            CASE_TYPE_ID,
            caseRef
        );
    }

}
