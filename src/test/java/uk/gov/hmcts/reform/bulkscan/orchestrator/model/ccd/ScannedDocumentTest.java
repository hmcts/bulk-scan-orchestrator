package uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ScannedDocumentTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    void should_preserve_trailing_zero() throws JsonProcessingException {
        Instant scanningDate = Instant.parse("2022-05-23T01:02:03.120Z");
        Instant deliveryDate = Instant.parse("2022-05-23T01:02:03.220Z");
        ScannedDocument scannedDocument = new ScannedDocument(
                "fileName",
                "controlNumber",
                "type",
                "subtype",
                toLocalDateTime(scanningDate),
                new CcdDocument("https://localhost/files/" + UUID.randomUUID(), null),
                toLocalDateTime(deliveryDate),
                null // this should always be null;
        );

        String documentSerialized = objectMapper.writeValueAsString(scannedDocument);
        assertThat(documentSerialized.contains(".120Z")).isTrue();
        assertThat(documentSerialized.contains(".220Z")).isTrue();
    }

    @Test
    void should_preserve_trailing_two_zeroes() throws JsonProcessingException {
        Instant scanningDate = Instant.parse("2022-05-23T01:02:03.100Z");
        Instant deliveryDate = Instant.parse("2022-05-23T01:02:03.200Z");
        ScannedDocument scannedDocument = new ScannedDocument(
                "fileName",
                "controlNumber",
                "type",
                "subtype",
                toLocalDateTime(scanningDate),
                new CcdDocument("https://localhost/files/" + UUID.randomUUID(), null),
                toLocalDateTime(deliveryDate),
                null // this should always be null;
        );

        String documentSerialized = objectMapper.writeValueAsString(scannedDocument);
        assertThat(documentSerialized.contains(".100Z")).isTrue();
        assertThat(documentSerialized.contains(".200Z")).isTrue();
    }

    @Test
    void should_preserve_trailing_three_zeroes() throws JsonProcessingException {
        Instant scanningDate = Instant.parse("2022-05-23T01:02:03.000Z");
        Instant deliveryDate = Instant.parse("2022-05-23T01:02:03.000Z");
        ScannedDocument scannedDocument = new ScannedDocument(
                "fileName",
                "controlNumber",
                "type",
                "subtype",
                toLocalDateTime(scanningDate),
                new CcdDocument("https://localhost/files/" + UUID.randomUUID(), null),
                toLocalDateTime(deliveryDate),
                null // this should always be null;
        );

        String documentSerialized = objectMapper.writeValueAsString(scannedDocument);
        assertThat(documentSerialized.contains(".000Z")).isTrue();
    }

    @Test
    void should_preserve_trailing_none_zeroes() throws JsonProcessingException {
        Instant scanningDate = Instant.parse("2022-05-23T01:02:03.123Z");
        Instant deliveryDate = Instant.parse("2022-05-23T01:02:03.223Z");
        ScannedDocument scannedDocument = new ScannedDocument(
                "fileName",
                "controlNumber",
                "type",
                "subtype",
                toLocalDateTime(scanningDate),
                new CcdDocument("https://localhost/files/" + UUID.randomUUID(), null),
                toLocalDateTime(deliveryDate),
                null // this should always be null;
        );

        String documentSerialized = objectMapper.writeValueAsString(scannedDocument);
        assertThat(documentSerialized.contains(".123Z")).isTrue();
        assertThat(documentSerialized.contains(".223Z")).isTrue();
    }

    private LocalDateTime toLocalDateTime(Instant instant) {
        return ZonedDateTime.ofInstant(instant, ZoneId.systemDefault()).toLocalDateTime();
    }
}