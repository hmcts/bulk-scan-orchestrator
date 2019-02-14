package uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.mappers;

import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.SupplementaryEvidence;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Document;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.mappers.DocumentMapper.mapDocuments;

@Component
public class SupplementaryEvidenceMapper {

    public SupplementaryEvidenceMapper() {
        // empty mapper construct
    }

    public SupplementaryEvidence map(List<Document> existingDocs, List<Document> envelopeDocs) {

        Stream<Document> docsToAdd =
            envelopeDocs
                .stream()
                .filter(d -> existingDocs.stream().noneMatch(e -> areDuplicates(d, e)));

        return new SupplementaryEvidence(
            mapDocuments(
                Stream.concat(
                    existingDocs.stream(),
                    docsToAdd
                ).collect(toList())
            )
        );
    }

    private boolean areDuplicates(Document d1, Document d2) {
        return Objects.equals(d1.url, d2.url)
            || Objects.equals(d1.controlNumber, d2.controlNumber);
    }
}
