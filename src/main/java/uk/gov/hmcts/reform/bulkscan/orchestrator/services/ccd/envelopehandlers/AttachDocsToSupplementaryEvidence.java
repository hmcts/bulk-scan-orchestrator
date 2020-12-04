package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.envelopehandlers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.SupplementaryEvidence;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.mappers.SupplementaryEvidenceMapper;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CcdApi;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CcdAuthenticator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.EventIds;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Document;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Envelope;
import uk.gov.hmcts.reform.ccd.client.model.CaseDataContent;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.ccd.client.model.Event;
import uk.gov.hmcts.reform.ccd.client.model.StartEventResponse;

import java.util.List;
import java.util.Map;

import static uk.gov.hmcts.reform.bulkscan.orchestrator.helper.ScannedDocumentsHelper.getDocuments;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.ServiceCaseFields.BULK_SCAN_ENVELOPES;

@Component
class AttachDocsToSupplementaryEvidence {

    private static final Logger log = LoggerFactory.getLogger(AttachDocsToSupplementaryEvidence.class);

    private static final String EVENT_SUMMARY = "Attach scanned documents";

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
            return true;
        } else {
            log.info("Attaching supplementary evidence. {}", loggingContext);

            try {
                CcdAuthenticator authenticator = ccdApi.authenticateJurisdiction(envelope.jurisdiction);

                ccdApi.attachScannedDocs(
                    authenticator,
                    envelope.jurisdiction,
                    existingCase.getCaseTypeId(),
                    Long.toString(existingCase.getId()),
                    EventIds.ATTACH_SCANNED_DOCS,
                    startEventResponse -> buildCaseDataContent(envelope, startEventResponse),
                    loggingContext
                );

                log.info("Attached documents from envelope to case. {}", loggingContext);
                return true;
            } catch (UnableToAttachDocumentsException e) {
                log.error("Failed to attach documents from envelope to case. {}", loggingContext, e);
                return false;
            }
        }
    }

    @SuppressWarnings("unchecked")
    private CaseDataContent buildCaseDataContent(
        Envelope envelope,
        StartEventResponse startEventResponse
    ) {
        CaseDetails caseDetails = startEventResponse.getCaseDetails();
        var envelopeReferences = (List<Map<String, Object>>)caseDetails.getData().get(BULK_SCAN_ENVELOPES);

        final List<Document> existingDocuments = getDocuments(caseDetails);
        for (Document document : existingDocuments) {
            if (document.fileName == null) {
                log.error("null fileName of existing document");
            }
        }
        SupplementaryEvidence caseData = mapper.map(existingDocuments, envelopeReferences, envelope);

        return CaseDataContent.builder()
            .eventToken(startEventResponse.getToken())
            .event(Event.builder()
                .id(EventIds.ATTACH_SCANNED_DOCS)
                .summary(EVENT_SUMMARY)
                .build())
            .data(caseData)
            .build();
    }
}
