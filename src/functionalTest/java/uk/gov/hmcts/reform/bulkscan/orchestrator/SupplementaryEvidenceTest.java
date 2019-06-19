package uk.gov.hmcts.reform.bulkscan.orchestrator;

import com.google.common.base.Strings;
import org.apache.commons.lang3.StringUtils;
import org.awaitility.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import uk.gov.hmcts.reform.bulkscan.orchestrator.dm.DocumentManagementUploadService;
import uk.gov.hmcts.reform.bulkscan.orchestrator.helper.CcdCaseCreator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.helper.EnvelopeMessager;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.ScannedDocument;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CcdApi;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Document;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.helper.CaseDataExtractor.getScannedDocuments;

@SpringBootTest
@ActiveProfiles("nosb") // no servicebus queue handler registration
class SupplementaryEvidenceTest {

    private static final String TEST_SERVICE_NAME = "bulkscan";

    @Autowired
    private CcdApi ccdApi;

    @Autowired
    private CcdCaseCreator ccdCaseCreator;

    @Autowired
    private EnvelopeMessager envelopeMessager;

    @Autowired
    private DocumentManagementUploadService dmUploadService;

    @Test
    public void should_attach_supplementary_evidence_to_the_case_with_no_evidence_docs() throws Exception {
        //given
        String dmUrl = dmUploadService.uploadToDmStore("Evidence2.pdf", "documents/supplementary-evidence.pdf");
        CaseDetails caseDetails = ccdCaseCreator.createCase(emptyList());

        // when
        envelopeMessager.sendMessageFromFile(
            "envelopes/supplementary-evidence-envelope.json",
            String.valueOf(caseDetails.getId()),
            null,
            randomUUID(),
            dmUrl
        );

        // then
        assertThat(dmUrl).isNotNull();
        await("Supplementary evidence is attached to the case in ccd")
            .atMost(60, TimeUnit.SECONDS)
            .pollInterval(Duration.TWO_SECONDS)
            .until(() -> hasCaseBeenUpdatedWithSupplementaryEvidence(caseDetails, 1));
    }

    @Test
    public void should_attach_supplementary_evidence_to_the_case_with_existing_evidence_docs() throws Exception {
        //given
        String dmUrlOriginal = dmUploadService.uploadToDmStore("original.pdf", "documents/supplementary-evidence.pdf");
        String documentUuid = StringUtils.substringAfterLast(dmUrlOriginal, "/");
        String dmUrlNew = dmUploadService.uploadToDmStore("new.pdf", "documents/supplementary-evidence.pdf");

        CaseDetails caseDetails =
            ccdCaseCreator.createCase(
                singletonList(
                    new Document("evidence.pdf", "123", "other", null, Instant.now(), documentUuid)));

        // when
        envelopeMessager.sendMessageFromFile(
            "envelopes/supplementary-evidence-envelope.json",
            String.valueOf(caseDetails.getId()),
            null,
            randomUUID(),
            dmUrlNew
        );

        // then
        await("Supplementary evidence is attached to the case in ccd")
            .atMost(60, TimeUnit.SECONDS)
            .pollInterval(Duration.TWO_SECONDS)
            .until(() -> hasCaseBeenUpdatedWithSupplementaryEvidence(caseDetails, 2));
    }

    @Test
    public void should_be_able_to_attach_supplementary_evidence_to_case_by_legacy_id() throws Exception {
        //given
        String dmUrl = dmUploadService.uploadToDmStore("Evidence2.pdf", "documents/supplementary-evidence.pdf");
        CaseDetails caseDetails = ccdCaseCreator.createCase(emptyList());
        String legacyId = (String) caseDetails.getData().get("legacyId");

        await("The new case can be found by legacy ID")
            .atMost(30, TimeUnit.SECONDS)
            .pollInterval(Duration.ONE_SECOND)
            .until(() -> !ccdApi.getCaseRefsByLegacyId(legacyId, TEST_SERVICE_NAME).isEmpty());

        // when
        envelopeMessager.sendMessageFromFile(
            "envelopes/supplementary-evidence-with-legacy-id.json",
            null,
            legacyId,
            randomUUID(),
            dmUrl
        );

        // then
        assertThat(dmUrl).isNotNull();
        await("Supplementary evidence is attached to the case in ccd")
            .atMost(60, TimeUnit.SECONDS)
            .pollInterval(Duration.TWO_SECONDS)
            .until(() -> hasCaseBeenUpdatedWithSupplementaryEvidence(caseDetails, 1));
    }

    private boolean hasCaseBeenUpdatedWithSupplementaryEvidence(
        CaseDetails caseDetails, int excpectedScannedDocuments
    ) {
        CaseDetails updatedCaseDetails = ccdApi.getCase(
            String.valueOf(caseDetails.getId()),
            caseDetails.getJurisdiction()
        );
        String evidenceHandled = Strings.nullToEmpty(
            (String) updatedCaseDetails.getData().getOrDefault("evidenceHandled", "NO_VALUE")
        );

        List<ScannedDocument> updatedScannedDocuments = getScannedDocuments(updatedCaseDetails);
        return updatedScannedDocuments.size() == excpectedScannedDocuments && evidenceHandled.equals("No");
    }
}
