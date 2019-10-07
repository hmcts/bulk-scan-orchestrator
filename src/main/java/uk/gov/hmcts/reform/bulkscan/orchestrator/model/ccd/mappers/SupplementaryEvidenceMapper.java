package uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.mappers;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.SupplementaryEvidence;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Document;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.mappers.DocumentMapper.mapDocuments;

@Component
public class SupplementaryEvidenceMapper {

    private final String documentManagementUrl;
    private final String contextPath;

    public SupplementaryEvidenceMapper(
        @Value("${document_management.url}") final String documentManagementUrl,
        @Value("${document_management.context-path}") final String contextPath
    ) {
        this.documentManagementUrl = documentManagementUrl;
        this.contextPath = contextPath;
    }

    public SupplementaryEvidence map(List<Document> existingDocs, List<Document> envelopeDocs, Instant deliveryDate) {
        return new SupplementaryEvidence(
            mapDocuments(
                Stream.concat(
                    existingDocs.stream(),
                    getDocsToAdd(existingDocs, envelopeDocs).stream()
                ).collect(toList()),
                documentManagementUrl,
                contextPath,
                deliveryDate
            )
        );
    }

    public List<Document> getDocsToAdd(List<Document> existingDocs, List<Document> newDocs) {
        return newDocs
            .stream()
            .filter(d -> existingDocs.stream().noneMatch(e -> areDuplicates(d, e)))
            .collect(toList());
    }

    private boolean areDuplicates(Document d1, Document d2) {
        return Objects.equals(d1.uuid, d2.uuid)
            || Objects.equals(d1.controlNumber, d2.controlNumber);
    }
}
