package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;
import uk.gov.hmcts.reform.ccd.client.CoreCaseDataApi;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.ccd.client.model.StartEventResponse;

@Service
public class CaseRetriever {
    private static final Logger log = LoggerFactory.getLogger(CaseRetriever.class);

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
            authenticator.userDetails.getId(),
            jurisdiction,
            CASE_TYPE_ID,
            caseRef
        );
    }

    public void updateCase(Envelope envelope) {
        CcdAuthenticator info = factory.createForJurisdiction(envelope.jurisdiction);
        updateCase(envelope, info);
    }

    private void updateCase(Envelope envelope, CcdAuthenticator authenticator) {
        StartEventResponse startEventResponse = coreCaseDataApi.startEventForCaseWorker(
            authenticator.getUserToken(),
            authenticator.getServiceToken(),
            authenticator.userDetails.getId(),
            envelope.jurisdiction,
            CASE_TYPE_ID,
            envelope.caseRef,
            "attachRecord"
        );

        CaseDetails caseDetails = startEventResponse.getCaseDetails();
        log.info("Found worker case: {}:{}:{}",
            caseDetails.getJurisdiction(),
            caseDetails.getCaseTypeId(),
            caseDetails.getId());
    }

}
