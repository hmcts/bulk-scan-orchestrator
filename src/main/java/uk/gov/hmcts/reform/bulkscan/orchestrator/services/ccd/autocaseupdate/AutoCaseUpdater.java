package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.autocaseupdate;

import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGenerator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.caseupdate.model.response.SuccessfulUpdateResponse;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.caseupdatedetails.CaseUpdateDetailsService;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CcdApi;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Envelope;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.util.List;

@Service
public class AutoCaseUpdater {

    private final AuthTokenGenerator s2sTokenGenerator;
    private final CaseUpdateDetailsService caseUpdateDataService;
    private final CcdApi ccdApi;

    public AutoCaseUpdater(
        AuthTokenGenerator s2sTokenGenerator,
        CaseUpdateDetailsService caseUpdateDataService,
        CcdApi ccdApi
    ) {
        this.s2sTokenGenerator = s2sTokenGenerator;
        this.caseUpdateDataService = caseUpdateDataService;
        this.ccdApi = ccdApi;
    }

    public void updateCase(Envelope envelope) {
        List<Long> matchingCases = ccdApi.getCaseRefsByEnvelopeId(envelope.id, envelope.container);

        switch (matchingCases.size()) {
            case 0:
                handleNoMatchingCases();
                break;
            case 1:
                handleSingleMatchingCase(matchingCases.get(0), envelope);
                break;
            default:
                handleMultipleMatchingCases();
                break;
        }
    }

    private void handleSingleMatchingCase(Long caseId, Envelope envelope) {
        CaseDetails existingCase = ccdApi.getCase(String.valueOf(caseId), envelope.jurisdiction);

        SuccessfulUpdateResponse caseUpdateResult =
            caseUpdateDataService.getCaseUpdateData("?", existingCase, envelope);

        if (!caseUpdateResult.warnings.isEmpty()) {
            // stop here
        } else {

        }
    }

    private void handleNoMatchingCases() {

    }

    private void handleMultipleMatchingCases() {

    }


}
