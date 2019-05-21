package uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.mappers;

import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.CcdDocument;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.ScannedDocument;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Document;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentMapperTest {

    @Test
    void should_map_document_properly() {
        // given
        Document doc = new Document(
            "name.zip",
            "123",
            "type",
            "subtype",
            Instant.now(),
            "https://localhost/files/uuid1",
            "uuid1"
        );

        // when
        ScannedDocument result = DocumentMapper.mapDocument(doc, "https://localhost/files");

        // then
        assertThat(result)
            .isEqualToComparingFieldByField(
                new ScannedDocument(
                    doc.fileName,
                    doc.controlNumber,
                    doc.type,
                    doc.subtype,
                    ZonedDateTime.ofInstant(doc.scannedAt, ZoneId.systemDefault()).toLocalDateTime(),
                    new CcdDocument("https://localhost/files/" + doc.uuid),
                    null // this should always be null;
                )
            );
    }

    @Test
    void should_map_null_document_properly() {
        // given
        Document doc = null;

        // when
        ScannedDocument result = DocumentMapper.mapDocument(doc, "https://localhost/files");

        // then
        assertThat(result).isNull();
    }

    @Test
    void should_map_null_scanned_date() {
        // given
        Document doc = new Document("name.zip", "123", "type", "subtype", null, "https://localhost/files/uuid1", "uuid1");

        // when
        ScannedDocument result = DocumentMapper.mapDocument(doc, "https://localhost/files");

        // then
        assertThat(result.scannedDate).isNull();
    }
}
