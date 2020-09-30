package uk.gov.hmcts.reform.bulkscan.orchestrator;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import uk.gov.hmcts.reform.bulkscan.orchestrator.dm.DocumentManagementUploadService;
import uk.gov.hmcts.reform.bulkscan.orchestrator.helper.CaseSearcher;
import uk.gov.hmcts.reform.bulkscan.orchestrator.helper.CcdCaseCreator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.helper.EnvelopeMessager;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CcdApi;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static java.util.Collections.emptyList;
import static java.util.UUID.randomUUID;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@ActiveProfiles("nosb")
public class AutomaticCaseUpdateTest {

    @Autowired CcdApi ccdApi;
    @Autowired CaseSearcher caseSearcher;
    @Autowired EnvelopeMessager envelopeMessager;
    @Autowired DocumentManagementUploadService dmUploadService;
    @Autowired CcdCaseCreator ccdCaseCreator;

    @Test
    @SuppressWarnings("unchecked")
    void should_update_a_case() throws Exception {
        //given
        String docUrl = dmUploadService.uploadToDmStore("Certificate.pdf", "documents/supplementary-evidence.pdf");
        CaseDetails existingCase = ccdCaseCreator.createCase(emptyList(), Instant.now());

        // when
        envelopeMessager.sendMessageFromFile(
            "envelopes/supplementary-evidence-with-ocr-bulkscanauto.json",
            String.valueOf(existingCase.getId()),
            null,
            randomUUID(),
            docUrl
        );

        // then
        await("Case in CCD was updated with data from envelope")
            .atMost(60, TimeUnit.SECONDS)
            .pollInterval(Duration.ofSeconds(2))
            .until(() -> {
                CaseDetails updatedCaseDetails = ccdApi.getCase(
                    String.valueOf(existingCase.getId()),
                    existingCase.getJurisdiction()
                );
                Map<String, String> address = (Map<String, String>) updatedCaseDetails.getData().get("address");
                return Objects.equals(
                    address.get("country"),
                    "country-from-envelope"
                );
            });
    }
}
