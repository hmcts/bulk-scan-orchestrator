package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import com.google.common.base.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.util.List;
import java.util.Optional;

@Service
public class CaseFinder {

    private static final Logger log = LoggerFactory.getLogger(CaseFinder.class);

    private final CcdApi ccdApi;

    public CaseFinder(CcdApi ccdApi) {
        this.ccdApi = ccdApi;
    }

    public Optional<CaseDetails> findCase(Envelope envelope) {
        Optional<CaseDetails> caseDetails = Strings.isNullOrEmpty(envelope.caseRef)
            ? Optional.empty()
            : getCaseByCcdId(envelope.caseRef, envelope.jurisdiction);

        if (caseDetails.isPresent()) {
            return caseDetails;
        } else if (!Strings.isNullOrEmpty(envelope.legacyCaseRef)) {
            return getCaseByLegacyId(envelope);
        } else {
            return Optional.empty();
        }
    }

    private Optional<CaseDetails> getCaseByLegacyId(Envelope envelope) {
        List<Long> ccdCaseRefs =
            ccdApi.getCaseRefsByLegacyId(envelope.legacyCaseRef, envelope.container);

        if (ccdCaseRefs.size() == 1) {
            Long caseCcdRef = ccdCaseRefs.get(0);

            Optional<CaseDetails> details =
                getCaseByCcdId(String.valueOf(caseCcdRef), envelope.jurisdiction);

            logCaseRetrievalResultBasedOnLegacyIdSearch(
                details.isPresent(),
                caseCcdRef,
                envelope.legacyCaseRef,
                envelope.id);

            return details;
        } else {
            logWrongNumberOfSearchResultsByLegacyId(
                ccdCaseRefs,
                envelope.legacyCaseRef,
                envelope.id
            );

            return Optional.empty();
        }
    }

    private void logWrongNumberOfSearchResultsByLegacyId(
        List<Long> ccdCaseIds,
        String legacyCaseRef,
        String envelopeId
    ) {
        if (ccdCaseIds.isEmpty()) {
            log.info(
                "Case not found by legacy ID '{}'. Envelope ID: '{}'",
                legacyCaseRef,
                envelopeId
            );
        } else {
            log.warn(
                "Multiple cases found for legacy ID '{}'. Envelope ID: '{}'",
                legacyCaseRef,
                envelopeId
            );
        }
    }

    private void logCaseRetrievalResultBasedOnLegacyIdSearch(
        boolean caseFound,
        Long ccdCaseRef,
        String legacyCaseRef,
        String envelopeId
    ) {
        if (caseFound) {
            log.info(
                "Found case for legacy ID '{}'. Case CCD ID: '{}'. Envelope ID: '{}'",
                legacyCaseRef,
                ccdCaseRef,
                envelopeId
            );
        } else {
            String messageFormat =
                "Case was found by legacy ID, but subsequent read from CCD couldn't find it. "
                    + "Legacy ID: '{}', CCD ID: '{}', Envelope ID: '{}'";

            log.error(
                messageFormat,
                legacyCaseRef,
                ccdCaseRef,
                envelopeId
            );
        }
    }

    private Optional<CaseDetails> getCaseByCcdId(String ccdCaseRef, String jurisdiction) {
        try {
            return Optional.of(ccdApi.getCase(ccdCaseRef, jurisdiction));
        } catch (CaseNotFoundException e) {
            return Optional.empty();
        } catch (Exception e) {
            throw e;
        }
    }
}
