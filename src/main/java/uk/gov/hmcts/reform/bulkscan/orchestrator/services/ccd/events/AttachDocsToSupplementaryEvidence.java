package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.CaseData;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.mappers.SupplementaryEvidenceMapper;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CcdApi;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CcdAuthenticator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Envelope;
import uk.gov.hmcts.reform.ccd.client.model.CaseDataContent;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.ccd.client.model.Event;
import uk.gov.hmcts.reform.ccd.client.model.StartEventResponse;

import static uk.gov.hmcts.reform.bulkscan.orchestrator.helper.ScannedDocumentsHelper.getDocuments;

@Component
class AttachDocsToSupplementaryEvidence {

    private static final Logger log = LoggerFactory.getLogger(AttachDocsToSupplementaryEvidence.class);

    public static final String EVENT_TYPE_ID = "attachScannedDocs";
    public static final String EVENT_SUMMARY = "Attach scanned documents";

    private final SupplementaryEvidenceMapper mapper;
    private final CcdApi ccdApi;

    public AttachDocsToSupplementaryEvidence(
        SupplementaryEvidenceMapper mapper,
        CcdApi ccdApi
    ) {
        this.mapper = mapper;
        this.ccdApi = ccdApi;
    }

    /**
     * Attaches documents from given envelope to existing case.
     *
     * @return true when attaching documents to existing case is successful, otherwise false
     */
    public boolean attach(Envelope envelope, CaseDetails existingCase) {
        String loggingContext = String.format(
            "Envelope ID: %s. File name: %s. Case ref: %s. Case state: %s",
            envelope.id,
            envelope.zipFileName,
            existingCase.getId(),
            existingCase.getState()
        );
        if (mapper.getDocsToAdd(getDocuments(existingCase), envelope.documents).isEmpty()) {
            log.warn("Envelope has no new documents. CCD Case not updated. {}", loggingContext);
        } else {
            log.info("Attaching supplementary evidence. {}", loggingContext);

            try {
                CcdAuthenticator authenticator = ccdApi.authenticateJurisdiction(envelope.jurisdiction);

                StartEventResponse startEventResp = ccdApi.startEventForAttachScannedDocs(
                    authenticator,
                    envelope.jurisdiction,
                    existingCase.getCaseTypeId(),
                    existingCase.getId().toString(),
                    EVENT_TYPE_ID
                );

                log.info("Started event in CCD to attach exception record to case. {}", loggingContext);

                ccdApi.submitEventForAttachScannedDocs(
                    authenticator,
                    envelope.jurisdiction,
                    existingCase.getCaseTypeId(),
                    existingCase.getId().toString(),
                    buildCaseDataContent(envelope, startEventResp)
                );

                log.info("Attached documents from envelope to case. {}", loggingContext);
            } catch (UnableToAttachDocumentsException e) {
                log.error("Failed to attach documents from envelope to case. {}", loggingContext);
                return false;
            }
        }
        return true;
    }

    private CaseDataContent buildCaseDataContent(Envelope envelope, StartEventResponse startEventResponse) {
        CaseData caseData = mapper.map(
            getDocuments(startEventResponse.getCaseDetails()),
            envelope.documents,
            envelope.deliveryDate
        );
        return CaseDataContent.builder()
            .eventToken(startEventResponse.getToken())
            .event(Event.builder()
                .id(EVENT_TYPE_ID)
                .summary(EVENT_SUMMARY)
                .build())
            .data(caseData)
            .build();
    }
}
