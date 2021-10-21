package uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.mappers;

import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.CcdCollectionElement;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.CcdDocument;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.ScannedDocument;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Document;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

class DocumentMapperTest {

    @Test
    void should_map_document_properly() {
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

        // when
        List<CcdCollectionElement<ScannedDocument>> result =
                DocumentMapper.mapDocuments(
                        singletonList(new DocumentHashProvider.DocumentAndHash(doc, "hash")),
                        "https://localhost",
                        "files",
                        deliveryDate
                );

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).value)
            .isEqualToComparingFieldByField(
                new ScannedDocument(
                    doc.fileName,
                    doc.controlNumber,
                    doc.type,
                    doc.subtype,
                    toLocalDateTime(doc.scannedAt),
                    new CcdDocument("https://localhost/files/" + doc.uuid, "hash"),
                    toLocalDateTime(doc.deliveryDate),
                    null // this should always be null;
                )
            );
    }

    @Test
    void should_map_null_document_properly() {
        // given
        Document doc = null;

        // when
        List<CcdCollectionElement<ScannedDocument>> result =
                DocumentMapper.mapDocuments(
                        singletonList(new DocumentHashProvider.DocumentAndHash(doc, null)),
                        "https://localhost",
                        "files",
                        Instant.now()
                );

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
                DocumentMapper.mapDocuments(
                        singletonList(new DocumentHashProvider.DocumentAndHash(doc, "hash")),
                        "https://localhost",
                        "files",
                        Instant.now()
                );

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).value.scannedDate).isNull();
    }

    private LocalDateTime toLocalDateTime(Instant instant) {
        return ZonedDateTime.ofInstant(instant, ZoneId.systemDefault()).toLocalDateTime();
    }
}
