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
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.EnvelopeEventProcessor;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;

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
            .atMost(60, TimeUnit.SECONDS)
            .pollInterval(Duration.FIVE_SECONDS)
            .until(() -> hasExceptionRecordBeenCreated(randomPoBox));
    }

    private boolean hasExceptionRecordBeenCreated(UUID randomPoBox) {
        List<CaseDetails> caseDetails = caseSearcher.search(
            SampleData.JURSIDICTION,
            EnvelopeEventProcessor.EXCEPTION_RECORD_CASE_TYPE,
            ImmutableMap.of(
                "case.poBox", randomPoBox.toString()
            )
        );

        return caseDetails.size() == 1;
    }
}
