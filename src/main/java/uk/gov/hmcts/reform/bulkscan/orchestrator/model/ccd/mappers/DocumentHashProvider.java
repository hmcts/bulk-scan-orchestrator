package uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.mappers;

import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.cdam.CdamApiClient;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Document;

import java.util.List;

import static java.util.stream.Collectors.toList;

@Component
public class DocumentHashProvider {
    private final CdamApiClient cdamApiClient;

    public DocumentHashProvider(CdamApiClient cdamApiClient) {
        this.cdamApiClient = cdamApiClient;
    }

    public List<DocumentAndHash> getDocumentHashes(List<Document> documents, String jurisdiction) {
        return documents
                .stream()
                .map(d -> new DocumentHashProvider.DocumentAndHash(d, cdamApiClient.getDocumentHash(jurisdiction, d)))
                .collect(toList());
    }

    public static class DocumentAndHash {
        public final Document document;
        public final String hash;

        public DocumentAndHash(Document document, String hash) {
            this.document = document;
            this.hash = hash;
        }
    }
}
