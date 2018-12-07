package uk.gov.hmcts.reform.bulkscan.orchestrator;

import com.google.common.base.Strings;
import org.awaitility.Duration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import uk.gov.hmcts.reform.bulkscan.orchestrator.dm.DocumentManagementUploadService;
import uk.gov.hmcts.reform.bulkscan.orchestrator.helper.CcdCaseCreator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.helper.EnvelopeMessager;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.ScannedDocument;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CaseRetriever;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.EnvelopeParser;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.helper.ScannedDocumentsExtractor.getScannedDocuments;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@ActiveProfiles("nosb") // no servicebus queue handler registration
public class SupplementaryEvidenceTest {

    @Autowired
    private CaseRetriever caseRetriever;

    @Autowired
    private CcdCaseCreator ccdCaseCreator;

    @Autowired
    private EnvelopeMessager envelopeMessager;

    private CaseDetails caseDetails;

    private List<ScannedDocument> updatedScannedDocuments;

    private String dmUrl;

    @Autowired
    private DocumentManagementUploadService dmUploadService;

    private Envelope newEnvelope;
    private UUID randomPoBox;

    @BeforeEach
    public void setup() {

        dmUrl = dmUploadService.uploadToDmStore(
            "Evidence2.pdf",
            "documents/supplementary-evidence.pdf"
        );
        randomPoBox = UUID.randomUUID();
    }

    @Disabled
    @Test
    public void should_attach_supplementary_evidence_to_the_case_with_no_evidence_docs() throws Exception {
        //given
        String caseData = SampleData.fileContentAsString("envelopes/new-envelope.json");
        newEnvelope = EnvelopeParser.parse(caseData);
        caseDetails = ccdCaseCreator.createCase(newEnvelope);

        // when
        envelopeMessager.sendMessageFromFile(
            "envelopes/supplementary-evidence-envelope.json",
            String.valueOf(caseDetails.getId()),
            randomPoBox,
            dmUrl
        );

        // then
        assertThat(dmUrl).isNotNull();
        await("Supplementary evidence is attached to the case in ccd")
            .atMost(60, TimeUnit.SECONDS)
            .pollInterval(Duration.TWO_SECONDS)
            .until(() -> hasCaseBeenUpdatedWithSupplementaryEvidence(caseDetails, 1));

        verifySupplementaryEvidenceDetailsUpdated(1);
    }

    @Disabled
    @Test
    public void should_attach_supplementary_evidence_to_the_case_with_existing_evidence_docs() throws Exception {
        //given
        JSONObject newCaseData = updateEnvelope("envelopes/new-envelope-with-evidence.json", null);
        newEnvelope = EnvelopeParser.parse(newCaseData.toString());
        caseDetails = ccdCaseCreator.createCase(newEnvelope);

        // when
        envelopeMessager.sendMessageFromFile(
            "envelopes/supplementary-evidence-envelope.json",
            String.valueOf(caseDetails.getId()),
            randomPoBox,
            dmUrl
        );

        // then
        assertThat(dmUrl).isNotNull();
        await("Supplementary evidence is attached to the case in ccd")
            .atMost(60, TimeUnit.SECONDS)
            .pollInterval(Duration.TWO_SECONDS)
            .until(() -> hasCaseBeenUpdatedWithSupplementaryEvidence(caseDetails, 2));

        verifySupplementaryEvidenceDetailsUpdated(2);
    }

    private boolean hasCaseBeenUpdatedWithSupplementaryEvidence(
        CaseDetails caseDetails, int excpectedScannedDocuments
    ) {
        CaseDetails updatedCaseDetails = caseRetriever.retrieve(
            caseDetails.getJurisdiction(),
            String.valueOf(caseDetails.getId())
        );
        String evidenceHandled = Strings.nullToEmpty(
            (String) updatedCaseDetails.getData().getOrDefault("evidenceHandled", "NO_VALUE")
        );

        updatedScannedDocuments = getScannedDocuments(updatedCaseDetails);
        return updatedScannedDocuments.size() == excpectedScannedDocuments && evidenceHandled.equals("No");
    }

    private void verifySupplementaryEvidenceDetailsUpdated(int expectedScannedDocuments) throws JSONException {

        JSONObject updatedCaseData = updateEnvelope(
            "envelopes/supplementary-evidence-envelope.json",
            caseDetails.getId()
        );

        Envelope updatedEnvelope = EnvelopeParser.parse(updatedCaseData.toString());
        List<ScannedDocument> scannedDocumentList = getScannedDocuments(newEnvelope);

        List<ScannedDocument> scannedDocumentList2 = getScannedDocuments(updatedEnvelope);
        if (!scannedDocumentList2.isEmpty()) {
            scannedDocumentList.addAll(scannedDocumentList2);
        }

        assertThat(updatedScannedDocuments).isNotEmpty();
        assertThat(updatedScannedDocuments.size()).isEqualTo(expectedScannedDocuments);
        assertThat(scannedDocumentList.size()).isEqualTo(expectedScannedDocuments);
        assertThat(scannedDocumentList).containsAll(updatedScannedDocuments);
    }

    @NotNull
    private JSONObject updateEnvelope(String fileName, @Nullable Long caseRef) throws JSONException {
        String updatedCase = SampleData.fileContentAsString(fileName);
        JSONObject updatedCaseData = new JSONObject(updatedCase);

        if (caseRef != null) {
            updatedCaseData.put("case_ref", caseRef);
        }

        JSONArray documents = updatedCaseData.getJSONArray("documents");
        JSONObject document = (JSONObject) documents.get(0);
        document.put("url", dmUrl);
        return updatedCaseData;
    }
}
