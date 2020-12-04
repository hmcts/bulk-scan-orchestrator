package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.autocaseupdate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.caseupdatedetails.CaseUpdateDetailsService;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CaseFinder;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CcdApi;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.EventIds;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Envelope;

import static java.lang.String.format;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.autocaseupdate.AutoCaseUpdateResultType.ABANDONED;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.autocaseupdate.AutoCaseUpdateResultType.ERROR;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.autocaseupdate.AutoCaseUpdateResultType.OK;

/**
 * Updates a case based on data from envelope (without caseworkers intervention).
 */
@Service
public class AutoCaseUpdater {

    private static final Logger log = LoggerFactory.getLogger(AutoCaseUpdater.class);

    private final CaseUpdateDetailsService caseUpdateDataService;
    private final CaseFinder caseFinder;
    private final CcdApi ccdApi;
    private final CaseDataContentBuilderProvider caseDataBuilderProvider;

    // region constructor

    public AutoCaseUpdater(
        CaseUpdateDetailsService caseUpdateDataService,
        CaseFinder caseFinder,
        CcdApi ccdApi,
        CaseDataContentBuilderProvider caseDataBuilderProvider
    ) {
        this.caseUpdateDataService = caseUpdateDataService;
        this.caseFinder = caseFinder;
        this.ccdApi = ccdApi;
        this.caseDataBuilderProvider = caseDataBuilderProvider;
    }

    // endregion

    public AutoCaseUpdateResult updateCase(Envelope envelope) {
        try {
            return caseFinder
                .findCase(envelope)
                .map(
                    existingCase -> {
                        var caseUpdateResult =
                            caseUpdateDataService
                                .getCaseUpdateData(
                                    envelope.container,
                                    existingCase,
                                    envelope
                                );

                        ccdApi.updateCase(
                            existingCase.getJurisdiction(),
                            existingCase.getCaseTypeId(),
                            EventIds.ATTACH_SCANNED_DOCS_WITH_OCR,
                            existingCase.getId().toString(),
                            caseDataBuilderProvider.getBuilder(caseUpdateResult.caseDetails.caseData, envelope.id),
                            getLoggingInfo(envelope)
                        );

                        return new AutoCaseUpdateResult(OK, existingCase.getId());
                    }
                )
                .orElseGet(() -> {
                    log.warn("Auto case update abandoned - case not found for envelope. {}", getLoggingInfo(envelope));
                    return new AutoCaseUpdateResult(ABANDONED, null);
                });
        } catch (Exception exc) {
            log.error(
                "Error while trying to automatically update a case. {}",
                getLoggingInfo(envelope),
                exc
            );

            return new AutoCaseUpdateResult(ERROR, null);
        }
    }

    private String getLoggingInfo(Envelope envelope) {
        return format(
            "Envelope ID: %s. File name: %s. Service: %s. Case ref: %s. Legacy case ref: %s.",
            envelope.id,
            envelope.zipFileName,
            envelope.container,
            envelope.caseRef,
            envelope.legacyCaseRef
        );
    }
}
