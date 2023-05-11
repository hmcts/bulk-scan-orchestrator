package uk.gov.hmcts.reform.bulkscan.orchestrator;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import uk.gov.hmcts.reform.bulkscan.orchestrator.dm.DocumentManagementUploadService;
import uk.gov.hmcts.reform.bulkscan.orchestrator.helper.CaseSearcher;
import uk.gov.hmcts.reform.bulkscan.orchestrator.helper.CcdCaseCreator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.helper.EnvelopeMessager;
import uk.gov.hmcts.reform.bulkscan.orchestrator.helper.JmsEnvelopeMessager;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CcdApi;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static java.util.Collections.emptyList;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@ActiveProfiles("nosb")
public class AutomaticCaseUpdateTest {

    @Autowired
    CcdApi ccdApi;
    @Autowired
    CaseSearcher caseSearcher;
    @Autowired
    EnvelopeMessager envelopeMessager;
    @Autowired
    JmsEnvelopeMessager jmsEnvelopeMessager;
    @Autowired
    DocumentManagementUploadService dmUploadService;
    @Autowired
    CcdCaseCreator ccdCaseCreator;

    @Test
    @SuppressWarnings("unchecked")
    void should_update_a_case() throws Exception {
        //given
        String docUrl = dmUploadService.uploadToDmStore("Certificate.pdf", "documents/supplementary-evidence.pdf");
        CaseDetails existingCase = ccdCaseCreator.createCase(emptyList(), Instant.now());

        // when
        if (!Boolean.parseBoolean(System.getenv("JMS_ENABLED")))
            envelopeMessager.sendMessageFromFile(
                "envelopes/supplementary-evidence-with-ocr-bulkscanauto.json",
                String.valueOf(existingCase.getId()),
                null,
                docUrl
            );
        else
            jmsEnvelopeMessager.sendMessageFromFile(
                "envelopes/supplementary-evidence-with-ocr-bulkscanauto.json",
                String.valueOf(existingCase.getId()),
                null,
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
                var address = (Map<String, String>) updatedCaseDetails.getData().get("address");
                return address != null && Objects.equals(address.get("country"), "country-from-envelope");
            });
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_update_a_case_with_ocr_values_already_set() throws Exception {
        //given
        String docUrl = dmUploadService.uploadToDmStore("Certificate.pdf", "documents/supplementary-evidence.pdf");

        Map<String, Object> ocrFields = new HashMap<>();
        // the values are from envelopes/supplementary-evidence-with-ocr-bulkscanauto.json file
        ocrFields.put("firstName", "value1");
        ocrFields.put("lastName", "value2");
        CaseDetails existingCase = ccdCaseCreator.createCase(emptyList(), Instant.now(), ocrFields);

        // when
        if (!Boolean.parseBoolean(System.getenv("JMS_ENABLED")))
            envelopeMessager.sendMessageFromFile(
                "envelopes/supplementary-evidence-with-ocr-bulkscanauto.json",
                String.valueOf(existingCase.getId()),
                null,
                docUrl
            );
        else
            jmsEnvelopeMessager.sendMessageFromFile(
                "envelopes/supplementary-evidence-with-ocr-bulkscanauto.json",
                String.valueOf(existingCase.getId()),
                null,
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
                var address = (Map<String, String>) updatedCaseDetails.getData().get("address");
                return address != null && Objects.equals(address.get("country"), "country-from-envelope");
            });
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_update_a_case_with_ocr_values_set() throws Exception {
        //given
        String docUrl = dmUploadService.uploadToDmStore("Certificate.pdf", "documents/supplementary-evidence.pdf");

        Map<String, Object> ocrFields = new HashMap<>();
        // the values are from envelopes/supplementary-evidence-with-ocr-bulkscanauto.json file
        ocrFields.put("firstName", "value1");
        ocrFields.put("lastName", "value2");
        CaseDetails existingCase = ccdCaseCreator.createCase(emptyList(), Instant.now(), ocrFields);

        // when
        if (!Boolean.parseBoolean(System.getenv("JMS_ENABLED")))
            envelopeMessager.sendMessageFromFile(
                "envelopes/supplementary-evidence-with-ocr-bulkscanauto.json",
                String.valueOf(existingCase.getId()),
                null,
                docUrl
            );
        else
            jmsEnvelopeMessager.sendMessageFromFile(
                "envelopes/supplementary-evidence-with-ocr-bulkscanauto.json",
                String.valueOf(existingCase.getId()),
                null,
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
                var address = (Map<String, String>) updatedCaseDetails.getData().get("address");
                return Objects.equals(updatedCaseDetails.getData().get("firstName"), "value1")
                    && Objects.equals(updatedCaseDetails.getData().get("lastName"), "value2")
                    && address != null && Objects.equals(address.get("country"), "country-from-envelope");
            });

        // when
        if (!Boolean.parseBoolean(System.getenv("JMS_ENABLED")))
            envelopeMessager.sendMessageFromFile(
                "envelopes/supplementary-evidence-with-updated-ocr-bulkscanauto.json",
                String.valueOf(existingCase.getId()),
                null,
                docUrl
            );
        else
            jmsEnvelopeMessager.sendMessageFromFile(
                "envelopes/supplementary-evidence-with-updated-ocr-bulkscanauto.json",
                String.valueOf(existingCase.getId()),
                null,
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
                var address = (Map<String, String>) updatedCaseDetails.getData().get("address");
                return address != null && Objects.equals(address.get("country"), "updated-country-from-envelope");
            });
    }
}
