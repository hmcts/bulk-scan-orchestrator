package uk.gov.hmcts.reform.bulkscan.orchestrator.client.shared;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.cdam.CdamApiClient;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.request.DocumentType;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.request.ScannedDocument;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Document;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class DocumentMapperTest {

    private static final String JURISDICTION = "BULKSCAN";

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
        ScannedDocument result = documentMapper.toScannedDoc(doc, JURISDICTION);

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
        ScannedDocument result = documentMapper.toScannedDoc(doc, JURISDICTION);

        // then
        assertThat(result).isNull();
    }

    private LocalDateTime toLocalDateTime(Instant instant) {
        return instant == null
            ? null
            : ZonedDateTime.ofInstant(instant, ZoneId.systemDefault()).toLocalDateTime();
    }
}
