package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.CaseData;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.mappers.ModelMapper;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.mappers.SupplementaryEvidenceMapper;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Document;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;
import uk.gov.hmcts.reform.ccd.client.model.CaseDataContent;
import uk.gov.hmcts.reform.ccd.client.model.StartEventResponse;

import java.util.List;

import static uk.gov.hmcts.reform.bulkscan.orchestrator.helper.ScannedDocumentsHelper.getDocuments;

@Component
class AttachDocsToSupplementaryEvidence extends AbstractEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(AttachDocsToSupplementaryEvidence.class);

    private final ModelMapper<? extends CaseData> mapper;

    AttachDocsToSupplementaryEvidence(SupplementaryEvidenceMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    CaseData mapEnvelopeToCaseDataObject(Envelope envelope) {
        return mapper.mapEnvelope(envelope);
    }

    @Override
    public CaseDataContent buildCaseDataContent(StartEventResponse eventResponse, Envelope envelope) {
        List<Document> documents = getDocuments(eventResponse.getCaseDetails());
        log.info("Case Id: {} Existing Documents Size: {}", eventResponse.getCaseDetails().getId(), documents.size());

        envelope.addDocuments(documents);
        log.info("Documents Size after adding to envelope documents: {}", envelope.documents.size());

        return super.buildCaseDataContent(eventResponse, envelope);
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
