package uk.gov.hmcts.reform.bulkscan.orchestrator.services;

import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.ccd.client.CoreCaseDataApi;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

@Service
public class CcdCaseRetriever {
    public static final String CASE_TYPE_ID = "Bulk_Scanned";
    private final CoreCaseDataApi coreCaseDataApi;

    CcdCaseRetriever(CoreCaseDataApi coreCaseDataApi) {
        this.coreCaseDataApi = coreCaseDataApi;
    }

    CaseDetails retrieve(CcdAuthInfo authInfo, String caseRef) {
        return coreCaseDataApi.readForCaseWorker(authInfo.userToken,
            authInfo.serviceToken,
            authInfo.userDetails.getId(),
            authInfo.jurisdiction,
            CASE_TYPE_ID,
            caseRef);
    }

}
