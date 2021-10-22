package uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.mappers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.CaseAction;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.CcdCollectionElement;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.EnvelopeReference;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.SupplementaryEvidence;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.EnvelopeReferenceHelper;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Document;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Envelope;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.google.common.collect.Lists.newArrayList;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

@Component
public class SupplementaryEvidenceMapper {

    private static final Logger log = LoggerFactory.getLogger(SupplementaryEvidenceMapper.class);

    private final EnvelopeReferenceHelper envelopeReferenceHelper;
    private final DocMapper docMapper;

    public SupplementaryEvidenceMapper(
            final EnvelopeReferenceHelper envelopeReferenceHelper,
            DocMapper docMapper
    ) {
        this.envelopeReferenceHelper = envelopeReferenceHelper;
        this.docMapper = docMapper;
    }

    public SupplementaryEvidence map(
        List<Document> existingDocs,
        List<Map<String, Object>> existingEnvelopeReferences,
        Envelope envelope
    ) {
        log.info(String.format("Mapping documents: container %s, zipFileName %s, caseRef %s",
            envelope.container,
            envelope.zipFileName,
            envelope.caseRef
        ));
        List<CcdCollectionElement<EnvelopeReference>> updatedEnvelopeReferences =
            updateEnvelopeReferences(existingEnvelopeReferences, envelope);

        var scannedDocuments = docMapper.mapDocuments(
            Stream.concat(
                existingDocs.stream(),
                getDocsToAdd(existingDocs, envelope.documents).stream()
            ).collect(toList()),
            envelope.deliveryDate
        );

        return new SupplementaryEvidence(scannedDocuments, updatedEnvelopeReferences);
    }

    private List<CcdCollectionElement<EnvelopeReference>> updateEnvelopeReferences(
        List<Map<String, Object>> existingEnvelopeReferences,
        Envelope envelope
    ) {
        if (envelopeReferenceHelper.serviceSupportsEnvelopeReferences(envelope.container)) {
            var updatedEnvelopeReferences = newArrayList(
                envelopeReferenceHelper.parseEnvelopeReferences(existingEnvelopeReferences)
            );

            String existingReferences = updatedEnvelopeReferences
                .stream()
                .map(e -> "id:" + e.value.id + "-action:" + e.value.action)
                .collect(Collectors.joining(","));

            log.info(
                String.format(
                    "Update case: %s, zip file: %s, envelope id: %s, "
                        + "existing case has bulkscan refs: %s",
                    envelope.caseRef,
                    envelope.zipFileName,
                    envelope.id,
                    existingReferences
                )
            );
            updatedEnvelopeReferences.add(
                new CcdCollectionElement<>(new EnvelopeReference(envelope.id, CaseAction.UPDATE))
            );

            return updatedEnvelopeReferences;
        } else {
            return null;
        }
    }

    public List<Document> getDocsToAdd(List<Document> existingDocs, List<Document> newDocs) {
        logDocuments("Existing docs", existingDocs);
        logDocuments("New docs", newDocs);

        final List<Document> docsToAdd = newDocs
            .stream()
            .filter(d -> existingDocs.stream().noneMatch(e -> areDuplicates(d, e)))
            .collect(toList());

        logDocuments("Docs to add", docsToAdd);

        return docsToAdd;
    }

    private void logDocuments(String header, List<Document> docs) {
        final String docLogs = docs.stream()
            .map(doc -> "uuid: " + doc.uuid + ", dcn: " + doc.controlNumber + ", fileName: " + doc.fileName)
            .collect(joining("; "));
        log.info(String.format("%s: %s", header, docLogs));
    }

    private boolean areDuplicates(Document d1, Document d2) {
        return Objects.equals(d1.uuid, d2.uuid)
            || Objects.equals(d1.controlNumber, d2.controlNumber);
    }
}
