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
    private static final Logger log = LoggerFactory.getLogger(AbstractEventPublisher.class);

    private final SupplementaryEvidenceMapper mapper;

    AttachDocsToSupplementaryEvidence(SupplementaryEvidenceMapper mapper) {
        this.mapper = mapper;
    }

    public void handle(Envelope envelope, CaseDetails existingCase) {
        log.info("Adding new evidence to case {} from envelope {}", existingCase.getId(), envelope.id);

        if (mapper.getDocsToAdd(getDocuments(existingCase), envelope.documents).isEmpty()) {
            // an alert relies on this message, do not modify it.
            log.warn("Envelope has no new documents. CCD Case not updated");
        } else {
            publish(envelope, existingCase.getCaseTypeId());
        }
    }

    @Override
    CaseData buildCaseData(StartEventResponse eventResponse, Envelope envelope) {
        return mapper.map(
            getDocuments(eventResponse.getCaseDetails()),
            envelope.documents
        );
    }

    @Override
    String getEventTypeId() {
        return "attachScannedDocs";
    }

    @Override
    String getEventSummary() {
        return "Attach scanned documents";
    }
}
