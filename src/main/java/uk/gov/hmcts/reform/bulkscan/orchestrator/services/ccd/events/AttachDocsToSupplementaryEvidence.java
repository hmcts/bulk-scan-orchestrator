package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.CaseData;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.mappers.SupplementaryEvidenceMapper;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.ccd.client.model.StartEventResponse;

import static uk.gov.hmcts.reform.bulkscan.orchestrator.helper.ScannedDocumentsHelper.getDocuments;

@Component
class AttachDocsToSupplementaryEvidence extends AbstractEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(AttachDocsToSupplementaryEvidence.class);

    public static final String EVENT_TYPE_ID = "attachScannedDocs";
    public static final String EVENT_SUMMARY = "Attach scanned documents";

    private final SupplementaryEvidenceMapper mapper;

    AttachDocsToSupplementaryEvidence(SupplementaryEvidenceMapper mapper) {
        this.mapper = mapper;
    }

    public void handle(Envelope envelope, CaseDetails existingCase) {
        if (mapper.getDocsToAdd(getDocuments(existingCase), envelope.documents).isEmpty()) {
            log.warn("Envelope has no new documents. CCD Case not updated");
        } else {
            publish(envelope, existingCase.getCaseTypeId(), EVENT_TYPE_ID, EVENT_SUMMARY);
        }
    }

    @Override
    CaseData buildCaseData(StartEventResponse eventResponse, Envelope envelope) {
        return mapper.map(
            getDocuments(eventResponse.getCaseDetails()),
            envelope.documents
        );
    }
}
