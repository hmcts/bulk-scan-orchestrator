package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.autocaseupdate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.bulkscan.orchestrator.helper.CaseDataUpdater;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.CaseAction;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.caseupdatedetails.CaseUpdateDetailsService;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CcdApi;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.EventIds;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Envelope;
import uk.gov.hmcts.reform.ccd.client.model.CaseDataContent;
import uk.gov.hmcts.reform.ccd.client.model.Event;

import java.util.List;
import java.util.Map;

import static java.lang.String.format;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.autocaseupdate.AutoCaseUpdateResult.ABANDONED;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.autocaseupdate.AutoCaseUpdateResult.ERROR;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.autocaseupdate.AutoCaseUpdateResult.OK;

/**
 * Updates a case based on data from envelope (without caseworkers intervention).
 */
@Service
public class AutoCaseUpdater {

    private static final Logger log = LoggerFactory.getLogger(AutoCaseUpdater.class);

    private final CaseUpdateDetailsService caseUpdateDataService;
    private final CcdApi ccdApi;
    private final CaseDataUpdater caseDataUpdater;

    // region constructor
    public AutoCaseUpdater(
        CaseUpdateDetailsService caseUpdateDataService,
        CcdApi ccdApi,
        CaseDataUpdater caseDataUpdater
    ) {
        this.caseUpdateDataService = caseUpdateDataService;
        this.ccdApi = ccdApi;
        this.caseDataUpdater = caseDataUpdater;
    }
    // endregion

    public AutoCaseUpdateResult updateCase(Envelope envelope) {
        try {
            List<Long> matchingCases = ccdApi.getCaseRefsByEnvelopeId(envelope.id, envelope.container);

            switch (matchingCases.size()) {
                case 1:
                    String caseId = String.valueOf(matchingCases.get(0));
                    return handleSingleMatchingCase(caseId, envelope);
                case 0:
                    return handleNoMatchingCases(envelope);
                default:
                    return handleMultipleMatchingCases(matchingCases, envelope);
            }
        } catch (Exception exc) {
            log.error(
                "Error while trying to automatically update a case. {}",
                getLoggingInfo(envelope),
                exc
            );

            return ERROR;
        }
    }

    private AutoCaseUpdateResult handleSingleMatchingCase(String caseId, Envelope envelope) {
        var existingCase = ccdApi.getCase(caseId, envelope.jurisdiction);

        var caseUpdateResult =
            caseUpdateDataService
                .getCaseUpdateData(
                    envelope.container,
                    existingCase,
                    envelope
                );

        ccdApi.updateCase(
            envelope.container,
            existingCase.getCaseTypeId(),
            EventIds.ATTACH_SCANNED_DOCS_WITH_OCR,
            caseId,
            startEventResponse ->
                getCaseDataContent(
                    caseUpdateResult.caseDetails.caseData,
                    envelope.id,
                    startEventResponse.getEventId(),
                    startEventResponse.getToken()
                ),
            getLoggingInfo(envelope)
        );

        return OK;
    }

    private AutoCaseUpdateResult handleNoMatchingCases(Envelope envelope) {
        log.warn(
            "Auto case update abandoned - no case found for envelope. Envelope ID: {}, case ref: {}",
            envelope.id,
            envelope.caseRef
        );
        return ABANDONED;
    }

    private AutoCaseUpdateResult handleMultipleMatchingCases(List<Long> matchingCases, Envelope envelope) {
        log.warn(
            "Auto case update abandoned - multiple cases found for envelope. "
                + "Envelope ID: {}, target case ref: {}, found cases: {}",
            envelope.id,
            envelope.caseRef,
            matchingCases
        );
        return ABANDONED;
    }

    private CaseDataContent getCaseDataContent(
        Map<String, Object> caseData,
        String envelopeId,
        String eventId,
        String eventToken
    ) {

        return CaseDataContent
            .builder()
            .data(
                caseDataUpdater
                    .updateEnvelopeReferences(
                        caseData,
                        envelopeId,
                        CaseAction.UPDATE
                    ))
            .event(
                Event
                    .builder()
                    .id(eventId)
                    .summary("Case automatically updated with envelope")
                    .description("Case update with envelope " + envelopeId)
                    .build()
            )
            .eventToken(eventToken)
            .build();
    }

    private String getLoggingInfo(Envelope envelope) {
        return format(
            "Envelope ID: %s. File name: %s. Service: %s.",
            envelope.id,
            envelope.zipFileName,
            envelope.container
        );
    }
}
