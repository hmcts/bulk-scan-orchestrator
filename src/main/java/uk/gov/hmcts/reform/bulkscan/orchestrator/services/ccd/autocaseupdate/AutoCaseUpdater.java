package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.autocaseupdate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGenerator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.caseupdatedetails.CaseUpdateDetailsService;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CcdApi;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Envelope;

import java.util.List;

import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.autocaseupdate.AutoCaseUpdateResult.ABANDONED;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.autocaseupdate.AutoCaseUpdateResult.OK;

@Service
public class AutoCaseUpdater {

    private static final Logger log = LoggerFactory.getLogger(AutoCaseUpdater.class);

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

    public AutoCaseUpdateResult updateCase(Envelope envelope) {
        List<Long> matchingCases = ccdApi.getCaseRefsByEnvelopeId(envelope.id, envelope.container);

        switch (matchingCases.size()) {
            case 1:
                return handleSingleMatchingCase(matchingCases.get(0), envelope);
            case 0:
                return handleNoMatchingCases(envelope);
            default:
                return handleMultipleMatchingCases(matchingCases, envelope);
        }
    }

    private AutoCaseUpdateResult handleSingleMatchingCase(Long caseId, Envelope envelope) {
        var existingCase = ccdApi.getCase(String.valueOf(caseId), envelope.jurisdiction);

        var caseUpdateResult = caseUpdateDataService.getCaseUpdateData(envelope.container, existingCase, envelope);

        // TODO: update case
        return OK;
    }

    private AutoCaseUpdateResult handleNoMatchingCases(Envelope envelope) {
        log.warn(
            "No case found for envelope. Auto case update abandoned. Envelope ID: {}, case ref: {}",
            envelope.id,
            envelope.caseRef
        );
        return ABANDONED;
    }

    private AutoCaseUpdateResult handleMultipleMatchingCases(List<Long> matchingCases, Envelope envelope) {
        log.warn(
            "Multiple cases found for envelope. Auto case update abandoned. Envelope ID: {}, target case ref: {}, found cases: {}",
            envelope.id,
            envelope.caseRef,
            matchingCases
        );
        return ABANDONED;
    }
}
