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
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.envelopehandlers.CreateExceptionRecord;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.helper.CaseDataExtractor.getCaseDataForField;
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
        String envelopeId = UUID.randomUUID().toString();

        // when
        envelopeMessager.sendMessageFromFile(
            "envelopes/supplementary-evidence-envelope.json", // no payments
            "0000000000000000",
            null,
            dmUrl,
            envelopeId
        );

        // then
        await("Exception record being created")
            .atMost(60, TimeUnit.SECONDS)
            .pollInterval(Duration.ofSeconds(5))
            .until(() -> caseSearcher.findExceptionRecordByEnvelopeId(envelopeId, SampleData.CONTAINER).isPresent());

        CaseDetails caseDetails = caseSearcher.findExceptionRecordByEnvelopeId(
            envelopeId, SampleData.CONTAINER).get();
        assertThat(getCaseDataForField(caseDetails, "awaitingPaymentDCNProcessing")).isEqualTo("No");
        assertThat(getCaseDataForField(caseDetails, "containsPayments")).isEqualTo("No");
        assertThat(getCaseDataForField(caseDetails, "surname")).isNull();
    }

    @DisplayName("Should create ExceptionRecord when classification is NEW_APPLICATION")
    @Test
    public void should_create_exception_record_for_new_application() throws Exception {
        // given
        String envelopeId = UUID.randomUUID().toString();

        // when
        envelopeMessager.sendMessageFromFile(
            "envelopes/new-envelope-with-evidence.json", // with payments dcn
            "0000000000000000",
            null,
            dmUrl,
            envelopeId
        );

        // then
        await("Exception record should be created")
            .atMost(60, TimeUnit.SECONDS)
            .pollInterval(Duration.ofSeconds(5))
            .until(() -> caseSearcher.findExceptionRecordByEnvelopeId(envelopeId, SampleData.CONTAINER).isPresent());

        CaseDetails caseDetails = caseSearcher.findExceptionRecordByEnvelopeId(
            envelopeId, SampleData.CONTAINER).get();

        // envelope ID from the JSON resource representing the test message
        assertThat(caseDetails.getData().get("envelopeId")).isEqualTo(envelopeId);

        assertThat(caseDetails.getCaseTypeId()).isEqualTo("BULKSCAN_ExceptionRecord");
        assertThat(caseDetails.getJurisdiction()).isEqualTo("BULKSCAN");

        Map<String, String> expectedOcrData = ImmutableMap.of(
            "field1", "value1",
            "field2", "value2",
            "last_name", "surnameXXXX"
        );
        assertThat(getOcrData(caseDetails)).isEqualTo(expectedOcrData);
        List<String> expectedOcrDataWarnings = Arrays.asList("warning 1", "warning 2");
        assertThat(getOcrDataValidationWarnings(caseDetails)).isEqualTo(expectedOcrDataWarnings);

        assertThat(getCaseDataForField(caseDetails, "awaitingPaymentDCNProcessing")).isEqualTo("Yes");
        assertThat(getCaseDataForField(caseDetails, "containsPayments")).isEqualTo("Yes");
        assertThat(getCaseDataForField(caseDetails, "surname")).isEqualTo("surnameXXXX");
    }

    @DisplayName("Should create ExceptionRecord when provided/requested case reference is invalid")
    @Test
    public void create_exception_record_for_invalid_case_reference() throws Exception {
        // given
        String envelopeId = UUID.randomUUID().toString();

        // when
        envelopeMessager.sendMessageFromFile(
            "envelopes/supplementary-evidence-envelope.json",
            "1234",
            null,
            dmUrl,
            envelopeId
        );

        // then
        await("Exception record being created")
            .atMost(60, TimeUnit.SECONDS)
            .pollInterval(Duration.ofSeconds(5))
            .until(() -> caseSearcher.findExceptionRecordByEnvelopeId(envelopeId, SampleData.CONTAINER).isPresent());
    }

    @DisplayName("Should create ExceptionRecord when classification is SUPPLEMENTARY_EVIDENCE_WITH_OCR")
    @Test
    void create_exception_record_for_supplementary_evidence_with_ocr() throws Exception {
        //given
        String envelopeId = UUID.randomUUID().toString();
        String envelopeCaseRef = "1539860706648396";
        final Map<String, String> expectedOcrData = ImmutableMap.of(
            "first_name", "value1",
            "last_name", "value2",
            "email", "hello@test.com",
            "country", "sample_country"
        );

        // when
        envelopeMessager.sendMessageFromFile(
            "envelopes/supplementary-evidence-with-ocr-envelope.json",
            envelopeCaseRef,
            null,
            dmUrl,
            envelopeId
        );

        // then
        await("Exception record being created")
            .atMost(60, TimeUnit.SECONDS)
            .pollInterval(Duration.ofSeconds(5))
            .until(() -> findCasesByEnvelopeId(envelopeId).size() == 1);

        CaseDetails exceptionRecord = findCasesByEnvelopeId(envelopeId).get(0);
        assertThat(getCaseDataForField(exceptionRecord, "journeyClassification"))
            .isEqualTo("SUPPLEMENTARY_EVIDENCE_WITH_OCR");
        assertThat(getOcrData(exceptionRecord)).isEqualTo(expectedOcrData);
        assertThat(getCaseDataForField(exceptionRecord, "envelopeCaseReference")).isEqualTo(envelopeCaseRef);
        assertThat(getCaseDataForField(exceptionRecord, "envelopeLegacyCaseReference")).isEmpty();
    }

    private List<CaseDetails> findCasesByEnvelopeId(String envelopeId) {
        return caseSearcher.search(
            SampleData.JURSIDICTION,
            SampleData.CONTAINER.toUpperCase() + "_" + CreateExceptionRecord.CASE_TYPE,
            ImmutableMap.of(
                "case.envelopeId", envelopeId
            )
        );
    }
}
