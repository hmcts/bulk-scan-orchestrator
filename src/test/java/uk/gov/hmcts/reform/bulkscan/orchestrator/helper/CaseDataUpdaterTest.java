package uk.gov.hmcts.reform.bulkscan.orchestrator.helper;

import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.caseupdate.model.response.CaseUpdateDetails;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.request.DocumentUrl;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.ScannedDocument;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.internal.ExceptionRecord;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static java.time.LocalDateTime.now;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.fileContentAsBytes;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.objectMapper;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.request.DocumentType.FORM;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.ExceptionRecordFields.SCANNED_DOCUMENTS;

@SuppressWarnings({"unchecked", "checkstyle:LineLength"})
class CaseDataUpdaterTest {

    @Test
    void sets_exception_record_id_to_scanned_documents() throws Exception {
        // given
        var caseDataUpdater = new CaseDataUpdater();

        //contains documents with control numbers 1000, 2000, 3000
        var caseDetails = getCaseUpdateDetails("case-data/multiple-scanned-docs.json");
        var caseData = (Map<String, Object>) caseDetails.caseData;
        var scannedDocuments = asList(
            getScannedDocument("1000"),
            getScannedDocument("2000")
        );
        var exceptionRecord = new ExceptionRecord(
            "123456789",
            "caseTypeId",
            "envelopeId123",
            "poBox",
            "poBoxJurisdiction",
            Classification.EXCEPTION,
            "formType",
            now(),
            now(),
            scannedDocuments,
            emptyList()
        );

        // when
        var updatedCaseData = caseDataUpdater.setExceptionRecordIdToScannedDocuments(exceptionRecord, caseData);

        //then
        var updatedScannedDocuments = getScannedDocuments(updatedCaseData);
        assertThat(updatedScannedDocuments).hasSize(3);
        assertThat(updatedScannedDocuments.get(0).controlNumber).isEqualTo("1000");
        assertThat(updatedScannedDocuments.get(0).exceptionReference).isEqualTo(exceptionRecord.id);
        assertThat(updatedScannedDocuments.get(1).controlNumber).isEqualTo("2000");
        assertThat(updatedScannedDocuments.get(1).exceptionReference).isEqualTo(exceptionRecord.id);
        // not present in the exception record and should not be set
        assertThat(updatedScannedDocuments.get(2).controlNumber).isEqualTo("3000");
        assertThat(updatedScannedDocuments.get(2).exceptionReference).isNull();
    }

    private CaseUpdateDetails getCaseUpdateDetails(String resourceName) throws IOException {
        return objectMapper.readValue(fileContentAsBytes(resourceName), CaseUpdateDetails.class);
    }

    private List<ScannedDocument> getScannedDocuments(Map<String, Object> caseData) {
        return ((List<Map<String, Object>>) caseData.get(SCANNED_DOCUMENTS))
            .stream()
            .map(o -> objectMapper.convertValue(o.get("value"), ScannedDocument.class))
            .collect(toList());
    }

    private uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.request.ScannedDocument getScannedDocument(String controlNumber) {
        return new uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.request.ScannedDocument(
            FORM,
            "subtype",
            new DocumentUrl("url", "binaryUrl", "fileName"),
            controlNumber,
            "fileName",
            now(),
            now()
        );
    }

}
