package uk.gov.hmcts.reform.bulkscan.orchestrator.services;

import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.ccd.client.CoreCaseDataApi;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

@Service
class CcdCaseRetriever {
    private final CoreCaseDataApi coreCaseDataApi;

    CcdCaseRetriever(CoreCaseDataApi coreCaseDataApi) {
        this.coreCaseDataApi = coreCaseDataApi;
    }

    CaseDetails retrieve(CcdAuthInfo authInfo, String jurisdiction, String caseRef) {
        return coreCaseDataApi.readForCaseWorker(authInfo.userToken,
            authInfo.sscsToken,
            authInfo.userDetails.getId(),
            jurisdiction,
            "Bulk_Scanned",
            caseRef);
    }

}
