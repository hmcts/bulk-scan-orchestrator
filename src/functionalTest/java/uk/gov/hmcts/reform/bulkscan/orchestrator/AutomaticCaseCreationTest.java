package uk.gov.hmcts.reform.bulkscan.orchestrator;

import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import uk.gov.hmcts.reform.bulkscan.orchestrator.dm.DocumentManagementUploadService;
import uk.gov.hmcts.reform.bulkscan.orchestrator.helper.CaseSearcher;
import uk.gov.hmcts.reform.bulkscan.orchestrator.helper.CcdCaseCreator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.helper.EnvelopeMessager;
import uk.gov.hmcts.reform.bulkscan.orchestrator.helper.JmsEnvelopeMessager;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.CaseAction;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.CcdCollectionElement;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.EnvelopeReference;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static java.time.Instant.now;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.helper.CaseDataExtractor.getCaseDataForField;

@SpringBootTest
@ActiveProfiles("nosb")  // no servicebus queue handler registration
public class AutomaticCaseCreationTest {

    private static final String SERVICE_CASE_JURISDICTION = "BULKSCAN";
    private static final String SERVICE_CASE_TYPE_ID = "Bulk_Scanned";

    // case type ID for exception records related to service which is disabled for automatic case creation
    private static final String DISABLED_SERVICE_EXCEPTION_RECORD_CASE_TYPE_ID = "BULKSCAN_ExceptionRecord";

    // case type ID for exception records related to service which is enabled for automatic case creation
    private static final String ENABLED_SERVICE_EXCEPTION_RECORD_CASE_TYPE_ID = "BULKSCANAUTO_ExceptionRecord";

    @Autowired
    private CaseSearcher caseSearcher;

    @Autowired
    private EnvelopeMessager envelopeMessager;

    @Autowired
    private JmsEnvelopeMessager jmsEnvelopeMessager;

    @Autowired
    private DocumentManagementUploadService dmUploadService;

    @Autowired
    private CcdCaseCreator ccdCaseCreator;

    private String documentUrl;

    @BeforeEach
    void setup() {
        documentUrl = dmUploadService.uploadToDmStore(
            "Certificate.pdf",
            "documents/supplementary-evidence.pdf"
        );
    }

    @Test
    void should_not_create_more_than_one_case_for_the_same_envelope() throws Exception {
        // given a case has already been created from the envelope
        var envelopeId = UUID.randomUUID().toString();
        var originalFirstName = "TheNameFromOriginalCase";

        ccdCaseCreator.createCase(
            emptyList(),
            now(),
            ImmutableMap.of(
                "bulkScanEnvelopes",
                asList(new CcdCollectionElement<>(new EnvelopeReference(envelopeId, CaseAction.CREATE))),
                "firstName",
                originalFirstName
            )
        );

        waitForServiceCaseToBeInElasticSearch(envelopeId);

        // when
        sendEnvelopeMessage("envelopes/valid-new-application-bulkscanauto.json", envelopeId);

        // wait for the message to be processed and CCD's ElasticSearch to be updated
        Thread.sleep(5000);

        // then
        var cases = caseSearcher.searchByEnvelopeId(SERVICE_CASE_JURISDICTION, SERVICE_CASE_TYPE_ID, envelopeId);

        assertThat(cases.size()).isEqualTo(1);
        CaseDetails caseDetails = cases.get(0);

        assertCorrectEnvelopeReferences(envelopeId, caseDetails);

        // verify case data doesn't come from queue message
        assertThat(getCaseDataForField(caseDetails, "firstName")).isEqualTo(originalFirstName);

        // make sure the exception record wasn't created
        assertThat(findExceptionRecords(envelopeId, ENABLED_SERVICE_EXCEPTION_RECORD_CASE_TYPE_ID)).isEmpty();
    }

    @Test
    void should_create_exception_record_when_envelope_data_is_valid_but_service_is_disabled() throws Exception {
        // given
        // when
        String envelopeId = sendEnvelopeMessage(
            "envelopes/valid-new-application-bulkscan.json",
            UUID.randomUUID().toString()
        );

        // then
        waitForExceptionRecordToBeCreated(envelopeId, DISABLED_SERVICE_EXCEPTION_RECORD_CASE_TYPE_ID);

        var exceptionRecords = findExceptionRecords(envelopeId, DISABLED_SERVICE_EXCEPTION_RECORD_CASE_TYPE_ID);
        assertThat(exceptionRecords.size()).isOne();

        var serviceCases = caseSearcher.searchByEnvelopeId(
            SERVICE_CASE_JURISDICTION, SERVICE_CASE_TYPE_ID, envelopeId);
        assertThat(serviceCases).isEmpty();
    }

    private String sendEnvelopeMessage(String resourcePath, String envelopeId) throws Exception {
        return (!Boolean.parseBoolean(System.getenv("JMS_ENABLED")))
            ? envelopeMessager.sendMessageFromFile(
            resourcePath,
            "0000000000000000",
            null,
            documentUrl,
            envelopeId
        ) : jmsEnvelopeMessager.sendMessageFromFile(
            resourcePath,
            "0000000000000000",
            null,
            documentUrl,
            envelopeId);
    }

    private List<CaseDetails> findExceptionRecords(String envelopeId, String caseTypeId) {
        return caseSearcher.search(
            SERVICE_CASE_JURISDICTION,
            caseTypeId,
            ImmutableMap.of("case.envelopeId", envelopeId)
        );
    }

    @SuppressWarnings("unchecked")
    private void assertCorrectEnvelopeReferences(String envelopeId, CaseDetails caseDetails) {
        List<Map<String, Object>> envelopeReferences =
            (List<Map<String, Object>>) caseDetails.getData().get("bulkScanEnvelopes");

        assertThat(envelopeReferences.size()).isOne();
        assertThat(envelopeReferences.get(0).get("value")).isEqualTo(
            ImmutableMap.of(
                "id", envelopeId,
                "action", "create"
            )
        );
    }

    private void waitForServiceCaseToBeInElasticSearch(String envelopeId) {
        await("Wait for service case to be searchable in ElasticSearch. Envelope ID: " + envelopeId)
            .atMost(60, TimeUnit.SECONDS)
            .pollInterval(Duration.ofSeconds(5))
            .until(() ->
                !caseSearcher.searchByEnvelopeId(
                    SERVICE_CASE_JURISDICTION,
                    SERVICE_CASE_TYPE_ID,
                    envelopeId
                )
                    .isEmpty()
            );
    }

    private void waitForExceptionRecordToBeCreated(String envelopeId, String caseTypeId) {
        await("Wait for exception record to be created. Envelope ID: " + envelopeId)
            .atMost(60, TimeUnit.SECONDS)
            .pollInterval(Duration.ofSeconds(5))
            .until(() -> !findExceptionRecords(envelopeId, caseTypeId).isEmpty());
    }
}
