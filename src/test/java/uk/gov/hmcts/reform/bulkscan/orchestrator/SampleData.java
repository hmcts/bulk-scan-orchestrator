package uk.gov.hmcts.reform.bulkscan.orchestrator;

import org.json.JSONArray;
import org.json.JSONObject;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CcdAuthenticator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.idam.Credential;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Classification;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.idam.client.models.UserDetails;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;

import static java.lang.ClassLoader.getSystemResource;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.emptyList;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.DatetimeHelper.toIso8601;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CaseRetriever.CASE_TYPE_ID;

public class SampleData {
    public static final String SERVICE_TOKEN = "SERVICE_TOKEN";
    public static final String USER_TOKEN = "USER_token";
    public static final String USER_NAME = "USER_NAME";
    public static final String PASSWORD = "PASSWORD";
    public static final String USER_ID = "USER_ID";
    public static final String CASE_REF = "ABC123";
    public static final String JURSIDICTION = "SSCS";
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
        .caseTypeId(CASE_TYPE_ID)
        .build();

    public static String exampleJson = fromFile("envelopes/example.json");

    public static String envelopeJson() {
        try {
            return new JSONObject()
                .put("id", "eb9c3598-35fc-424e-b05a-902ee9f11d56")
                .put("case_ref", CASE_REF)
                .put("jurisdiction", JURSIDICTION)
                .put("zip_file_name", "zip-file-test.zip")
                .put("classification", Classification.NEW_APPLICATION)
                .put("documents", new JSONArray()
                    .put(new JSONObject()
                        .put("file_name", "hello.pdf")
                        .put("control_number", "control_number")
                        .put("type", "doc_type")
                        .put("scanned_at", toIso8601(Instant.EPOCH))
                        .put("url", "https://example.gov.uk/123")
                    )
                )
                .toString();
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

    private SampleData() {
        // util class
    }
}
