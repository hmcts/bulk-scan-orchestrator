package uk.gov.hmcts.reform.bulkscan.orchestrator;

import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import uk.gov.hmcts.reform.bulkscan.orchestrator.dm.DocumentManagementUploadService;
import uk.gov.hmcts.reform.bulkscan.orchestrator.helper.CaseSearcher;
import uk.gov.hmcts.reform.bulkscan.orchestrator.helper.EnvelopeMessager;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.events.CreateExceptionRecord;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.helper.CaseDataExtractor.getOcrData;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.helper.CaseDataExtractor.getOcrDataValidationWarnings;

@SpringBootTest
@ActiveProfiles("nosb")  // no servicebus queue handler registration
class ExceptionRecordCreationTest {

    @Autowired
    private CaseSearcher caseSearcher;

    @Autowired
    private EnvelopeMessager envelopeMessager;

    @Autowired
    private DocumentManagementUploadService dmUploadService;

    private String dmUrl;

    @BeforeEach
    void setup() {

        dmUrl = dmUploadService.uploadToDmStore(
            "Certificate.pdf",
            "documents/supplementary-evidence.pdf"
        );
    }

    @DisplayName("Should create ExceptionRecord when provided/requested supplementary evidence is not present")
    @Test
    public void create_exception_record_from_supplementary_evidence() throws Exception {
        // given
        UUID randomPoBox = UUID.randomUUID();

        // when
        envelopeMessager.sendMessageFromFile(
            "envelopes/supplementary-evidence-envelope.json",
            "0000000000000000",
            null,
            randomPoBox,
            dmUrl
        );

        // then
        await("Exception record being created")
            .atMost(60, TimeUnit.SECONDS)
            .pollInterval(Duration.ofSeconds(5))
            .until(() -> findCasesByPoBox(randomPoBox).size() == 1);
    }

    @DisplayName("Should create ExceptionRecord when classification is NEW_APPLICATION")
    @Test
    public void should_create_exception_record_for_new_application() throws Exception {
        // given
        UUID randomPoBox = UUID.randomUUID();

        // when
        envelopeMessager.sendMessageFromFile(
            "envelopes/new-envelope-with-evidence.json",
            "0000000000000000",
            null,
            randomPoBox,
            dmUrl
        );

        // then
        await("Exception record should be created")
            .atMost(60, TimeUnit.SECONDS)
            .pollInterval(Duration.ofSeconds(5))
            .until(() -> findCasesByPoBox(randomPoBox).size() == 1);

        CaseDetails caseDetails = findCasesByPoBox(randomPoBox).get(0);
        assertThat(caseDetails.getCaseTypeId()).isEqualTo("BULKSCAN_ExceptionRecord");
        assertThat(caseDetails.getJurisdiction()).isEqualTo("BULKSCAN");

        Map<String, String> expectedOcrData = ImmutableMap.of("field1", "value1", "field2", "value2");
        assertThat(getOcrData(caseDetails)).isEqualTo(expectedOcrData);

        List<String> expectedOcrDataWarnings = Arrays.asList("warning 1", "warning 2");
        assertThat(getOcrDataValidationWarnings(caseDetails)).isEqualTo(expectedOcrDataWarnings);
    }

    @DisplayName("Should create ExceptionRecord when provided/requested case reference is invalid")
    @Test
    public void create_exception_record_for_invalid_case_reference() throws Exception {
        // given
        UUID randomPoBox = UUID.randomUUID();

        // when
        envelopeMessager.sendMessageFromFile(
            "envelopes/supplementary-evidence-envelope.json",
            "1234",
            null,
            randomPoBox,
            dmUrl
        );

        // then
        await("Exception record being created")
            .atMost(60, TimeUnit.SECONDS)
            .pollInterval(Duration.ofSeconds(5))
            .until(() -> findCasesByPoBox(randomPoBox).size() == 1);
    }

    private List<CaseDetails> findCasesByPoBox(UUID poBox) {
        return caseSearcher.search(
            SampleData.JURSIDICTION,
            SampleData.CONTAINER.toUpperCase() + "_" + CreateExceptionRecord.CASE_TYPE,
            ImmutableMap.of(
                "case.poBox", poBox.toString()
            )
        );
    }
}
