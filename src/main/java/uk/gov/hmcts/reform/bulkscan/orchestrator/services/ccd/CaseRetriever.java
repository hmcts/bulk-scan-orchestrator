package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.ccd.client.CoreCaseDataApi;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

@Service
public class CaseRetriever {
    public static final String CASE_TYPE_ID = "Bulk_Scanned";

    private static final Logger log = LoggerFactory.getLogger(CaseRetriever.class);

    private final CcdAuthenticatorFactory factory;
    private final CoreCaseDataApi coreCaseDataApi;

    CaseRetriever(CcdAuthenticatorFactory factory, CoreCaseDataApi coreCaseDataApi) {
        this.factory = factory;
        this.coreCaseDataApi = coreCaseDataApi;
    }

    public CaseDetails retrieve(String jurisdiction, String caseRef) {
        CcdAuthenticator info = factory.createForJurisdiction(jurisdiction);
        CaseDetails caseDetails = retrieveCase(jurisdiction, caseRef, info);

        if (caseDetails != null) {
            log.info(
                "Found worker case: {}:{}:{}",
                caseDetails.getJurisdiction(),
                caseDetails.getCaseTypeId(),
                caseDetails.getId()
            );
        }

        return caseDetails;
    }

    private CaseDetails retrieveCase(String jurisdiction, String caseRef, CcdAuthenticator authenticator) {
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
