package uk.gov.hmcts.reform.bulkscan.orchestrator;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.json.JSONArray;
import org.json.JSONObject;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CcdAuthenticator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.idam.Credential;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Classification;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Document;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.idam.client.models.UserDetails;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.ClassLoader.getSystemResource;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.temporal.ChronoUnit.DAYS;
import static java.util.Collections.emptyList;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.DatetimeHelper.toIso8601;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.events.EventPublisher.BULK_SCANNED;

public class SampleData {
    public static final String SERVICE_TOKEN = "SERVICE_TOKEN";
    public static final String USER_TOKEN = "USER_token";
    public static final String USER_NAME = "USER_NAME";
    public static final String PASSWORD = "PASSWORD";
    public static final String USER_ID = "USER_ID";
    public static final String CASE_REF = "ABC123";
    public static final String JURSIDICTION = "BULKSCAN";
    public static final String PO_BOX = "BULKSCAN_PO_BOX";
    public static final long CASE_ID = 23L;

    public static final UserDetails USER_DETAILS = new UserDetails(USER_ID,
        null, null, null, emptyList());
    public static final Credential USER_CREDS = new Credential(USER_NAME, PASSWORD);
    public static final CcdAuthenticator AUTH_DETAILS = CcdAuthenticator.from(
        () -> SERVICE_TOKEN,
        USER_DETAILS,
        () -> USER_TOKEN);

    public static final CaseDetails THE_CASE = CaseDetails.builder()
        .id(CASE_ID)
        .jurisdiction(JURSIDICTION)
        .caseTypeId(BULK_SCANNED)
        .build();

    public static final ObjectMapper objectMapper;

    static {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS);
    }

    public static byte[] exampleJson = fromFile("envelopes/example.json").getBytes();

    public static byte[] envelopeJson() {
        return envelopeJson(Classification.SUPPLEMENTARY_EVIDENCE, CASE_REF);
    }

    public static byte[] envelopeJson(String caseRef) {
        return envelopeJson(Classification.SUPPLEMENTARY_EVIDENCE, caseRef);
    }

    public static byte[] envelopeJson(Classification classification) {
        return envelopeJson(classification, CASE_REF);
    }

    public static byte[] envelopeJson(Classification classification, String caseRef) {
        try {
            return new JSONObject()
                .put("id", "eb9c3598-35fc-424e-b05a-902ee9f11d56")
                .put("case_ref", caseRef)
                .put("po_box", PO_BOX)
                .put("jurisdiction", JURSIDICTION)
                .put("zip_file_name", "zip-file-test.zip")
                .put("delivery_date", toIso8601(Instant.now()))
                .put("opening_date", toIso8601(Instant.now()))
                .put("classification", classification)
                .put("documents", new JSONArray()
                    .put(new JSONObject()
                        .put("file_name", "hello.pdf")
                        .put("control_number", "control_number")
                        .put("type", "doc_type")
                        .put("scanned_at", toIso8601(Instant.EPOCH))
                        .put("url", "https://example.gov.uk/123")
                    )
                )
                .toString().getBytes();
        } catch (Exception e) {
            throw new RuntimeException("Could not make envelopeJson", e);
        }

    }

    public static String fromFile(String file) {
        try {
            Path fullPath = Paths.get(getSystemResource(file).toURI());
            byte[] bytes = Files.readAllBytes(fullPath);
            return new String(bytes, UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Could not load file" + file, e);
        }
    }

    public static Envelope envelope(int numberOfDocuments) {
        return new Envelope(
            "eb9c3598-35fc-424e-b05a-902ee9f11d56",
            CASE_REF,
            PO_BOX,
            JURSIDICTION,
            "zip-file-test.zip",
            Instant.now(),
            Instant.now(),
            Classification.NEW_APPLICATION,
            documents(numberOfDocuments)
        );
    }

    private static List<Document> documents(int numberOfDocuments) {
        return Stream.iterate(1, i -> i + 1)
            .map(index ->
                new Document(
                    String.format("file_%s.pdf", index),
                    String.format("control_number_%s", index),
                    String.format("type_%s", index),
                    LocalDate.parse("2018-10-01").plus(index, DAYS).atStartOfDay().toInstant(ZoneOffset.UTC),
                    String.format("https://example.gov.uk/%s", index)
                )
            ).limit(numberOfDocuments)
            .collect(Collectors.toList());
    }

    private SampleData() {
        // util class
    }
}
