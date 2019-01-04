package uk.gov.hmcts.reform.bulkscan.orchestrator;

import com.google.common.collect.ImmutableMap;
import com.microsoft.azure.servicebus.primitives.ServiceBusException;
import org.awaitility.Duration;
import org.json.JSONException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import uk.gov.hmcts.reform.bulkscan.orchestrator.dm.DocumentManagementUploadService;
import uk.gov.hmcts.reform.bulkscan.orchestrator.helper.CaseSearcher;
import uk.gov.hmcts.reform.bulkscan.orchestrator.helper.EnvelopeMessager;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.events.DelegatePublisher;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.helper.CaseDataExtractor.getOcrData;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@ActiveProfiles("nosb")  // no servicebus queue handler registration
public class ExceptionRecordCreationTest {

    @Autowired
    private CaseSearcher caseSearcher;

    @Autowired
    private EnvelopeMessager envelopeMessager;

    @Autowired
    private DocumentManagementUploadService dmUploadService;

    private String dmUrl;

    @BeforeEach
    public void setup() {

        dmUrl = dmUploadService.uploadToDmStore(
            "Certificate.pdf",
            "documents/supplementary-evidence.pdf"
        );
    }

    @DisplayName("Should create ExceptionRecord when provided/requested supplementary evidence is not present")
    @Test
    public void create_exception_record_from_supplementary_evidence()
        throws JSONException, InterruptedException, ServiceBusException {
        // given
        UUID randomPoBox = UUID.randomUUID();

        // when
        envelopeMessager.sendMessageFromFile(
            "envelopes/supplementary-evidence-envelope.json",
            "0000000000000000",
            randomPoBox,
            dmUrl
        );

        // then
        await("Exception record being created")
            .atMost(1000, TimeUnit.SECONDS)
            .pollInterval(Duration.FIVE_SECONDS)
            .until(() -> hasExceptionRecordBeenCreated(randomPoBox));
    }

    @DisplayName("Should create ExceptionRecord when classification is NEW_APPLICATION")
    @Test
    public void should_create_exception_record_for_new_application()
        throws JSONException, InterruptedException, ServiceBusException {
        // given
        UUID randomPoBox = UUID.randomUUID();

        // when
        envelopeMessager.sendMessageFromFile(
            "envelopes/new-envelope-with-evidence.json",
            "0000000000000000",
            randomPoBox,
            dmUrl
        );

        // then
        await("Exception record should be created")
            .atMost(120, TimeUnit.SECONDS)
            .pollInterval(Duration.FIVE_SECONDS)
            .until(() -> hasExceptionRecordBeenCreated(randomPoBox));

        CaseDetails caseDetails = findCasesByPoBox(randomPoBox).get(0);
        assertThat(caseDetails.getCaseTypeId()).isEqualTo("BULKSCAN_ExceptionRecord");
        assertThat(caseDetails.getJurisdiction()).isEqualTo("BULKSCAN");

        Map<String, String> expectedOcrData = ImmutableMap.of("field1", "value1", "field2", "value2");
        assertThat(getOcrData(caseDetails)).isEqualTo(expectedOcrData);
    }

    @DisplayName("Should create ExceptionRecord when provided/requested case reference is invalid")
    @Test
    public void create_exception_record_for_invalid_case_reference()
        throws JSONException, InterruptedException, ServiceBusException {
        // given
        UUID randomPoBox = UUID.randomUUID();

        // when
        envelopeMessager.sendMessageFromFile(
            "envelopes/supplementary-evidence-envelope.json",
            "1234",
            randomPoBox,
            dmUrl
        );

        // then
        await("Exception record being created")
            .atMost(120, TimeUnit.SECONDS)
            .pollInterval(Duration.FIVE_SECONDS)
            .until(() -> hasExceptionRecordBeenCreated(randomPoBox));
    }

    private boolean hasExceptionRecordBeenCreated(UUID poBox) {
        return findCasesByPoBox(poBox).size() == 1;
    }

    private List<CaseDetails> findCasesByPoBox(UUID poBox) {
        return caseSearcher.search(
            SampleData.JURSIDICTION,
            SampleData.JURSIDICTION + "_" + DelegatePublisher.EXCEPTION_RECORD_CASE_TYPE,
            ImmutableMap.of(
                "case.poBox", poBox.toString()
            )
        );
    }
}
