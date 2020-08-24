package uk.gov.hmcts.reform.bulkscan.orchestrator;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Resources;
import org.json.JSONArray;
import org.json.JSONObject;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CcdAuthenticator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.idam.Credential;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Document;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Envelope;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.OcrDataField;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Payment;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.idam.client.models.UserDetails;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.time.temporal.ChronoUnit.DAYS;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.DatetimeHelper.toIso8601;

public class SampleData {
    public static final String SERVICE_TOKEN = "SERVICE_TOKEN";
    public static final String USER_TOKEN = "USER_token";
    public static final String USER_NAME = "USER_NAME";
    public static final String PASSWORD = "PASSWORD";
    public static final String USER_ID = "USER_ID";
    public static final String CASE_REF = "ABC123";
    public static final String CASE_LEGACY_ID = "LEGACY-ID-123";
    public static final String ENVELOPE_ID = "eb9c3598-35fc-424e-b05a-902ee9f11d56";
    public static final String JURSIDICTION = "BULKSCAN";
    public static final String CONTAINER = "bulkscan";
    public static final String PO_BOX = "BULKSCAN_PO_BOX";
    public static final String FORM_TYPE = "FORM_TYPE";
    public static final long CASE_ID = 23L;
    public static final String EXAMPLE_JSON_FILE = "envelopes/example.json";
    public static final String BULK_SCANNED_CASE_TYPE = "Bulk_Scanned";

    public static final UserDetails USER_DETAILS = new UserDetails(USER_ID,
        null, null, null, emptyList()
    );
    public static final Credential USER_CREDS = new Credential(USER_NAME, PASSWORD);
    public static final CcdAuthenticator AUTH_DETAILS = new CcdAuthenticator(
        () -> SERVICE_TOKEN,
        USER_DETAILS,
        USER_TOKEN
    );

    public static final CaseDetails THE_CASE = CaseDetails.builder()
        .id(CASE_ID)
        .jurisdiction(JURSIDICTION)
        .caseTypeId(BULK_SCANNED_CASE_TYPE)
        .build();

    public static final ObjectMapper objectMapper;

    static {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS);
    }

    public static byte[] envelopeJson() {
        return envelopeJson(Classification.SUPPLEMENTARY_EVIDENCE, CASE_REF, ENVELOPE_ID);
    }

    public static byte[] envelopeJson(String caseRef) {
        return envelopeJson(Classification.SUPPLEMENTARY_EVIDENCE, caseRef, ENVELOPE_ID);
    }

    public static byte[] envelopeJson(Classification classification) {
        return envelopeJson(classification, CASE_REF, ENVELOPE_ID);
    }

    public static byte[] envelopeJson(Classification classification, String caseRef, String envelopeId) {
        try {
            return new JSONObject()
                .put("id", envelopeId)
                .put("case_ref", caseRef)
                .put("previous_service_case_ref", CASE_LEGACY_ID)
                .put("po_box", PO_BOX)
                .put("jurisdiction", JURSIDICTION)
                .put("container", CONTAINER)
                .put("zip_file_name", "zip-file-test.zip")
                .put("delivery_date", toIso8601(Instant.now()))
                .put("opening_date", toIso8601(Instant.now()))
                .put("classification", classification)
                .put("documents", new JSONArray()
                    .put(new JSONObject()
                        .put("file_name", "hello.pdf")
                        .put("control_number", "control_number")
                        .put("type", "doc_type")
                        .put("subtype", "doc_subtype")
                        .put("scanned_at", toIso8601(Instant.EPOCH))
                        .put("url", "https://example.gov.uk/uuid123")
                        .put("uuid", "uuid123")
                    )
                )
                .put("ocr_data_validation_warnings", new JSONArray())
                .toString().getBytes();
        } catch (Exception e) {
            throw new RuntimeException("Could not make envelopeJson", e);
        }

    }

    public static byte[] exampleJsonAsBytes() {
        return fileContentAsBytes(EXAMPLE_JSON_FILE);
    }

    public static String fileContentAsString(String file) {
        return new String(fileContentAsBytes(file), StandardCharsets.UTF_8);
    }

    public static byte[] fileContentAsBytes(String file) {
        try {
            return Resources.toByteArray(Resources.getResource(file));
        } catch (IOException e) {
            throw new RuntimeException("Could not load file" + file, e);
        }
    }

    public static Envelope envelope(int numberOfDocuments) {
        return envelope(
            numberOfDocuments,
            ImmutableList.of(new Payment("dcn1")),
            ImmutableList.of(new OcrDataField("fieldName1", "value1")),
            asList("warning 1", "warning 2")
        );
    }

    public static Envelope envelope(
        int numberOfDocuments,
        List<Payment> payments,
        List<OcrDataField> ocrData,
        List<String> ocrDataValidationWarnings
    ) {
        return new Envelope(
            ENVELOPE_ID,
            CASE_REF,
            CASE_LEGACY_ID,
            PO_BOX,
            JURSIDICTION,
            CONTAINER,
            "zip-file-test.zip",
            FORM_TYPE,
            Instant.now(),
            Instant.now(),
            Classification.NEW_APPLICATION,
            documents(numberOfDocuments),
            payments,
            ocrData,
            ocrDataValidationWarnings
        );
    }

    public static Envelope envelope(Classification classification, String jurisdiction, String caseRef) {
        return new Envelope(
            ENVELOPE_ID,
            caseRef,
            CASE_LEGACY_ID,
            PO_BOX,
            jurisdiction,
            CONTAINER,
            "zip-file-test.zip",
            FORM_TYPE,
            Instant.now(),
            Instant.now(),
            classification,
            documents(1),
            ImmutableList.of(new Payment("dcn1")),
            ImmutableList.of(new OcrDataField("fieldName1", "value1")),
            asList("warning 1", "warning 2")
        );
    }

    public static Envelope envelope(String caseRef, String legacyCaseRef, Classification classification) {
        return new Envelope(
            ENVELOPE_ID,
            caseRef,
            legacyCaseRef,
            PO_BOX,
            JURSIDICTION,
            CONTAINER,
            "zip-file-test.zip",
            FORM_TYPE,
            Instant.now(),
            Instant.now(),
            classification,
            documents(1),
            ImmutableList.of(new Payment("dcn1")),
            ImmutableList.of(new OcrDataField("fieldName1", "value1")),
            asList("warning 1", "warning 2")
        );
    }

    public static Envelope envelope(List<Document> documents, Instant deliveryDate) {
        return new Envelope(
            ENVELOPE_ID,
            CASE_REF,
            CASE_LEGACY_ID,
            PO_BOX,
            JURSIDICTION,
            CONTAINER,
            "zip-file-test.zip",
            FORM_TYPE,
            Instant.now(),
            Instant.now(),
            Classification.NEW_APPLICATION,
            documents,
            ImmutableList.of(new Payment("dcn1")),
            ImmutableList.of(new OcrDataField("fieldName1", "value1")),
            asList("warning 1", "warning 2")
        );
    }

    private static List<Document> documents(int numberOfDocuments) {
        return Stream.iterate(1, i -> i + 1)
            .map(index ->
                new Document(
                    String.format("file_%s.pdf", index),
                    String.format("control_number_%s", index),
                    String.format("type_%s", index),
                    String.format("subtype_%s", index),
                    ZonedDateTime.parse("2018-10-01T00:00:00Z").plus(index, DAYS).toInstant(),
                    String.format("uuid%s", index),
                    ZonedDateTime.parse("2018-10-01T00:00:00Z").minus(index, DAYS).toInstant()
                )
            ).limit(numberOfDocuments)
            .collect(Collectors.toList());
    }

    private SampleData() {
        // util class
    }
}
