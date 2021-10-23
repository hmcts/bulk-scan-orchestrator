package uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.mappers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.cdam.CdamApiClient;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.CcdCollectionElement;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.CcdDocument;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.ScannedDocument;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Document;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class DocMapperTest {
    private DocMapper docMapper;

    @Mock
    private CdamApiClient cdamApiClient;

    @BeforeEach
    public void setUp() {
        docMapper =
            new DocMapper("https://localhost", "files", cdamApiClient);
    }

    @Test
    void should_map_new_documents_and_existing_documents_properly() {
        // given
        Instant deliveryDate = Instant.now();
        Document doc = new Document(
            "name.zip",
            "123",
            "type",
            "subtype",
            Instant.now(),
            "uuid1",
            Instant.now()
        );

        Document existingDoc = new Document(
            "name2.zip",
            "222222",
            "type2",
            "subtype2",
            Instant.now(),
            "uuid2",
            Instant.now()
        );
        List<Document> newDocList = List.of(doc);
        String jurisdiction = "Bulk_Scan_Jur";
        String hashToken = "w3dsfwSADDAQ98754wq";
        given(cdamApiClient.getDocumentHash(jurisdiction, newDocList))
            .willReturn(Map.<String, String>of("uuid1", hashToken));

        // when
        List<CcdCollectionElement<ScannedDocument>> result =
                docMapper.mapDocuments(List.<Document>of(existingDoc), newDocList, deliveryDate, jurisdiction);

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).value)
            .usingRecursiveComparison()
            .isEqualTo(
                new ScannedDocument(
                    existingDoc.fileName,
                    existingDoc.controlNumber,
                    existingDoc.type,
                    existingDoc.subtype,
                    toLocalDateTime(existingDoc.scannedAt),
                    new CcdDocument("https://localhost/files/" + existingDoc.uuid, null),
                    toLocalDateTime(existingDoc.deliveryDate),
                    null // this should always be null;
                )
            );

        assertThat(result.get(1).value)
            .usingRecursiveComparison()
            .isEqualTo(
                new ScannedDocument(
                    doc.fileName,
                    doc.controlNumber,
                    doc.type,
                    doc.subtype,
                    toLocalDateTime(doc.scannedAt),
                    new CcdDocument("https://localhost/files/" + doc.uuid, hashToken),
                    toLocalDateTime(doc.deliveryDate),
                    null // this should always be null;
                )
            );
    }

    @Test
    void should_map_null_document_properly() {
        // given
        Document doc = null;
        String jurisdiction = "Jurisdiction";

        List<Document> docList = singletonList(doc);
        given(cdamApiClient.getDocumentHash(jurisdiction, docList)).willReturn(Map.of());
        // when
        List<CcdCollectionElement<ScannedDocument>> result =
                docMapper.mapDocuments(List.of(), singletonList(doc), Instant.now(), "Jurisdiction");

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).value).isNull();
    }

    @Test
    void should_map_null_scanned_date() {
        // given
        Document doc = new Document("name.zip", "123", "type", "subtype", null, "uuid1", Instant.now());

        // when
        List<CcdCollectionElement<ScannedDocument>> result =
                docMapper.mapDocuments(List.of(), singletonList(doc), Instant.now(), "Jurisdiction");

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).value.scannedDate).isNull();
    }

    private LocalDateTime toLocalDateTime(Instant instant) {
        return ZonedDateTime.ofInstant(instant, ZoneId.systemDefault()).toLocalDateTime();
    }
}
