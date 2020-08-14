package uk.gov.hmcts.reform.bulkscan.orchestrator;

import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import uk.gov.hmcts.reform.bulkscan.orchestrator.dm.DocumentManagementUploadService;
import uk.gov.hmcts.reform.bulkscan.orchestrator.helper.CaseSearcher;
import uk.gov.hmcts.reform.bulkscan.orchestrator.helper.EnvelopeMessager;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@ActiveProfiles("nosb")  // no servicebus queue handler registration
public class AutomaticCaseCreationTest {

    @Autowired
    private CaseSearcher caseSearcher;

    @Autowired
    private EnvelopeMessager envelopeMessager;

    @Autowired
    private DocumentManagementUploadService dmUploadService;

    private String documentUrl;

    @BeforeEach
    void setup() {
        documentUrl = dmUploadService.uploadToDmStore(
            "Certificate.pdf",
            "documents/supplementary-evidence.pdf"
        );
    }

    @SuppressWarnings("unchecked")
    @Test
    void should_create_case_when_envelope_data_is_valid() throws Exception {
        // when
        String envelopeId = sendEnvelopeMessage(
            "envelopes/valid-new-application-bulkscanauto.json",
            UUID.randomUUID()
        );

        // then
        await("Wait for service case to be created")
            .atMost(60, TimeUnit.SECONDS)
            .pollInterval(Duration.ofSeconds(5))
            .until(() -> !caseSearcher.searchByEnvelopeId("BULKSCAN", "Bulk_Scanned", envelopeId).isEmpty());

        var cases = caseSearcher.searchByEnvelopeId("BULKSCAN", "Bulk_Scanned", envelopeId);
        assertThat(cases.size()).isEqualTo(1);
        CaseDetails caseDetails = cases.get(0);

        List<Map<String, Object>> envelopeReferences =
            (List<Map<String, Object>>) caseDetails.getData().get("bulkScanEnvelopeReferences");

        assertThat(envelopeReferences.size()).isOne();
        assertThat(envelopeReferences.get(0).get("value")).isEqualTo(
            ImmutableMap.of(
                "id", envelopeId,
                "action", "create"
            )
        );
    }

    @Test
    void should_create_exception_record_when_envelope_data_is_valid_but_service_is_disabled() throws Exception {
        // given
        UUID poBox = UUID.randomUUID();

        // when
        sendEnvelopeMessage("envelopes/valid-new-application-bulkscan.json", poBox);

        // then
        await("Wait for exception record to be created")
            .atMost(60, TimeUnit.SECONDS)
            .pollInterval(Duration.ofSeconds(5))
            .until(() -> !findExceptionRecords(poBox, "BULKSCAN_ExceptionRecord").isEmpty());

        var exceptionRecords = findExceptionRecords(poBox, "BULKSCAN_ExceptionRecord");
        assertThat(exceptionRecords.size()).isOne();
    }

    @Test
    void should_create_exception_record_when_envelope_data_is_invalid() throws Exception {
        // given
        UUID poBox = UUID.randomUUID();

        // when
        sendEnvelopeMessage("envelopes/invalid-new-application-bulkscanauto.json", poBox);

        // then
        await("Wait for exception record to be created")
            .atMost(60, TimeUnit.SECONDS)
            .pollInterval(Duration.ofSeconds(5))
            .until(() -> !findExceptionRecords(poBox, "BULKSCANAUTO_ExceptionRecord").isEmpty());

        var exceptionRecords = findExceptionRecords(poBox, "BULKSCANAUTO_ExceptionRecord");
        assertThat(exceptionRecords.size()).isOne();
    }

    private String sendEnvelopeMessage(String resourcePath, UUID poBox) throws Exception {
        return envelopeMessager.sendMessageFromFile(
            resourcePath,
            "0000000000000000",
            null,
            poBox,
            documentUrl
        );
    }

    private List<CaseDetails> findExceptionRecords(UUID poBox, String caseTypeId) {
        return caseSearcher.search(
            "BULKSCAN",
            caseTypeId,
            ImmutableMap.of("case.poBox", poBox.toString())
        );
    }
}
