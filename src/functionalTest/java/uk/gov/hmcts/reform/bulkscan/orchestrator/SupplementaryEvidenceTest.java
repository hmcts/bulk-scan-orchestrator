package uk.gov.hmcts.reform.bulkscan.orchestrator;

import com.google.common.base.Strings;
import org.awaitility.Duration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
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
import java.util.concurrent.TimeUnit;

import static java.util.Collections.emptyList;
import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.helper.CaseDataExtractor.getScannedDocuments;

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
        String dmUrlNew = dmUploadService.uploadToDmStore("new.pdf", "documents/supplementary-evidence.pdf");

        // TODO: simply create a list of docs instead
        JSONObject newCaseData = updateEnvelope("envelopes/new-envelope-with-evidence.json", null, dmUrlOriginal);
        Envelope newEnvelope = EnvelopeParser.parse(newCaseData.toString());
        CaseDetails caseDetails = ccdCaseCreator.createCase(newEnvelope.documents);

        // when
        envelopeMessager.sendMessageFromFile(
            "envelopes/supplementary-evidence-envelope.json",
            String.valueOf(caseDetails.getId()),
            randomUUID(),
            dmUrlNew
        );

        // then
        await("Supplementary evidence is attached to the case in ccd")
            .atMost(60, TimeUnit.SECONDS)
            .pollInterval(Duration.TWO_SECONDS)
            .until(() -> hasCaseBeenUpdatedWithSupplementaryEvidence(caseDetails, 2));
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

        List<ScannedDocument> updatedScannedDocuments = getScannedDocuments(updatedCaseDetails);
        return updatedScannedDocuments.size() == excpectedScannedDocuments && evidenceHandled.equals("No");
    }

    @NotNull
    private JSONObject updateEnvelope(String fileName, @Nullable Long caseRef, String docUrl) throws JSONException {
        String updatedCase = SampleData.fileContentAsString(fileName);
        JSONObject updatedCaseData = new JSONObject(updatedCase);

        if (caseRef != null) {
            updatedCaseData.put("case_ref", caseRef);
        }

        JSONArray documents = updatedCaseData.getJSONArray("documents");
        JSONObject document = (JSONObject) documents.get(0);
        document.put("url", docUrl);
        return updatedCaseData;
    }
}
