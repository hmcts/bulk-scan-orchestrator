package uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.mappers;

import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.CcdDocument;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.ScannedDocument;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Document;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

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
            "https://localthost/files/1"
        );

        // when
        ScannedDocument result = DocumentMapper.mapDocument(doc, deliveryDate);

        // then
        assertThat(result)
            .isEqualToComparingFieldByField(
                new ScannedDocument(
                    doc.fileName,
                    doc.controlNumber,
                    doc.type,
                    doc.subtype,
                    toLocalDateTime(doc.scannedAt),
                    new CcdDocument(doc.url),
                    toLocalDateTime(deliveryDate),
                    null // this should always be null;
                )
            );
    }

    @Test
    void should_map_null_document_properly() {
        // given
        Document doc = null;

        // when
        ScannedDocument result = DocumentMapper.mapDocument(doc, Instant.now());

        // then
        assertThat(result).isNull();
    }

    @Test
    void should_map_null_scanned_date() {
        // given
        Document doc = new Document(
            "name.zip", "123", "type", "subtype", null, "https://localthost/files/1");

        // when
        ScannedDocument result = DocumentMapper.mapDocument(doc, Instant.now());

        // then
        assertThat(result.scannedDate).isNull();
    }

    private LocalDateTime toLocalDateTime(Instant instant) {
        return ZonedDateTime.ofInstant(instant, ZoneId.systemDefault()).toLocalDateTime();
    }
}
