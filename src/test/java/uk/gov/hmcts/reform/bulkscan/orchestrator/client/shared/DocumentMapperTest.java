package uk.gov.hmcts.reform.bulkscan.orchestrator.client.shared;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.cdam.CdamApiClient;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.request.DocumentType;
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

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@SuppressWarnings("checkstyle:LineLength")
@ExtendWith(MockitoExtension.class)
class DocumentMapperTest {

    final String dmUrl = "https://dm-url";
    final String dmContextPath = "hello";

    @Mock
    private CdamApiClient cdamApiClient;

    DocumentMapper documentMapper;

    @BeforeEach
    void setUp() {
        documentMapper = new DocumentMapper(dmUrl, dmContextPath, cdamApiClient);
    }

    @Test
    void should_map_document_correctly() {
        // given
        Document doc = new Document(
            "file-name",
            "dcn",
            "form",
            "subtype",
            Instant.now(),
            "123-123",
            Instant.now()
        );

        // when
        uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.request.ScannedDocument result = documentMapper.toScannedDoc(doc);

        // then
        assertThat(result.controlNumber).isEqualTo(doc.controlNumber);
        assertThat(result.fileName).isEqualTo(doc.fileName);
        assertThat(result.type).isEqualTo(DocumentType.FORM);
        assertThat(result.subtype).isEqualTo(doc.subtype);
        assertThat(result.documentUrl.filename).isEqualTo(doc.fileName);
        assertThat(result.documentUrl.url).isEqualTo("https://dm-url/hello/123-123");
        assertThat(result.documentUrl.binaryUrl).isEqualTo("https://dm-url/hello/123-123/binary");
        assertThat(result.deliveryDate).isEqualTo(toLocalDateTime(doc.deliveryDate));
        assertThat(result.scannedDate).isEqualTo(toLocalDateTime(doc.scannedAt));
    }

    @Test
    void should_handle_null() {
        // given
        Document doc = null;

        // when
        uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.request.ScannedDocument result = documentMapper.toScannedDoc(doc);

        // then
        assertThat(result).isNull();
    }

    @Test
    void should_map_to_ccd_scanned_documents_new_documents_and_existing_documents_properly() {
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
        String hash = "test-token-123";
        given(cdamApiClient.getDocumentHash(jurisdiction, newDocList))
                .willReturn(Map.<String, String>of("uuid1", hash));

        // when
        List<CcdCollectionElement<ScannedDocument>> result =
                documentMapper.mapToCcdScannedDocuments(singletonList(existingDoc), newDocList, deliveryDate, jurisdiction);

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
                    new CcdDocument("https://dm-url/hello/" + existingDoc.uuid, null),
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
                    new CcdDocument("https://dm-url/hello/" + doc.uuid, hash),
                    toLocalDateTime(doc.deliveryDate),
                    null // this should always be null;
                )
            );
    }

    @Test
    void should_map_to_ccd_scanned_documents_null_document_properly() {
        // given
        Document doc = null;
        String jurisdiction = "Jurisdiction";

        List<Document> docList = singletonList(doc);
        given(cdamApiClient.getDocumentHash(jurisdiction, docList)).willReturn(Map.of());

        // when
        List<CcdCollectionElement<ScannedDocument>> result =
                documentMapper.mapToCcdScannedDocuments(emptyList(), singletonList(doc), Instant.now(), "Jurisdiction");

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
                documentMapper.mapToCcdScannedDocuments(List.of(), singletonList(doc), Instant.now(), "Jurisdiction");

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).value.scannedDate).isNull();
    }

    private LocalDateTime toLocalDateTime(Instant instant) {
        return instant == null
            ? null
            : ZonedDateTime.ofInstant(instant, ZoneId.systemDefault()).toLocalDateTime();
    }
}
