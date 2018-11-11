package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.events;

import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.CaseData;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.CcdCollectionElement;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.ScannedDocument;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.SupplementaryEvidence;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.mappers.ModelMapper;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.mappers.SupplementaryEvidenceMapper;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;
import uk.gov.hmcts.reform.ccd.client.model.CaseDataContent;
import uk.gov.hmcts.reform.ccd.client.model.Event;
import uk.gov.hmcts.reform.ccd.client.model.StartEventResponse;

import java.util.List;
import java.util.stream.Collectors;

import static uk.gov.hmcts.reform.bulkscan.orchestrator.helper.ScannedDocumentsHelper.getScannedDocuments;

@Component
class AttachDocsToSupplementaryEvidence extends AbstractEventPublisher {

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

        List<ScannedDocument> ccdScannedDocuments = getScannedDocuments(
            eventResponse.getCaseDetails(), "scannedDocuments"
        );

        List<ScannedDocument> scannedDocuments = getScannedDocuments(envelope);

        ccdScannedDocuments.addAll(scannedDocuments);

        CaseData supplementaryEvidence = new SupplementaryEvidence(
            ccdScannedDocuments.stream().map(CcdCollectionElement::new).collect(Collectors.toList())
        );

        return CaseDataContent.builder()
            .eventToken(eventResponse.getToken())
            .event(Event.builder()
                .id(getEventTypeId())
                .summary(getEventSummary())
                .build())
            .data(supplementaryEvidence)
            .build();
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
