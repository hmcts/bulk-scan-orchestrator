package uk.gov.hmcts.reform.bulkscan.orchestrator.helper;

import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.caseupdate.model.response.CaseUpdateDetails;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.request.DocumentUrl;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.request.ExceptionRecord;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.request.ScannedDocument;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Document;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.time.LocalDateTime.now;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.fileContentAsBytes;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.objectMapper;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.request.DocumentType.FORM;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.ExceptionRecordFields.SCANNED_DOCUMENTS;

@SuppressWarnings("unchecked")
class ScannedDocumentsHelperTest {

    private static final String EXCEPTION_REFERENCE = "1";

    @Test
    void getDocuments_should_extract_all_documents() throws Exception {
        CaseDetails caseDetails = getCaseDetails("case-data/multiple-scanned-docs.json");

        List<Document> documents = ScannedDocumentsHelper.getDocuments(caseDetails);

        assertThat(documents)
            .extracting(doc -> doc.controlNumber)
            .containsExactlyInAnyOrder("1000", "2000", "3000");
    }

    @Test
    void getDocuments_should_return_empty_collection_when_scannedDocuments_are_missing()
        throws Exception {
        CaseDetails caseDetails = getCaseDetails("case-data/missing-scanned-documents.json");

        List<Document> documents = ScannedDocumentsHelper.getDocuments(caseDetails);

        assertThat(documents).isEmpty();
    }

    @Test
    void getDocuments_should_map_document_properties_correctly() throws Exception {
        CaseDetails caseDetails = getCaseDetails("case-data/single-scanned-doc.json");

        List<Document> documents = ScannedDocumentsHelper.getDocuments(caseDetails);

        assertThat(documents.size()).isOne();
        Document document = documents.get(0);

        Document expectedDocument = new Document(
            "filename1.pdf",
            "1111001",
            "Other",
            null,
            Instant.parse("2018-12-01T12:34:56.123Z"),
            "863c495e-d05b-4376-9951-ea489360db6f",
            Instant.parse("2018-12-02T12:30:56.123Z")
        );

        assertThat(document).isEqualToComparingFieldByField(expectedDocument);
    }

    @Test
    void should_handle_null_fields_in_document() throws Exception {
        // given
        CaseDetails caseDetails = getCaseDetails("case-data/null-fields-in-doc.json");

        // when
        List<Document> documents = ScannedDocumentsHelper.getDocuments(caseDetails);

        // then
        assertThat(documents).hasSize(1);
        assertThat(documents.get(0))
            .isEqualToComparingFieldByField(
                new Document(null, null, null, null, null, null, null)
            );
    }

    @Test
    void should_handle_null_document() throws Exception {
        // given
        CaseDetails caseDetails = getCaseDetails("case-data/null-doc.json");

        // when
        List<Document> documents = ScannedDocumentsHelper.getDocuments(caseDetails);

        // then
        assertThat(documents).hasSize(1);
        assertThat(documents.get(0)).isNull();
    }

    @Test
    void sets_exception_record_id_to_scanned_documents() throws Exception {
        // given
        //contains documents with control numbers 1000, 2000, 3000
        var caseDetails = getCaseUpdateDetails("case-data/multiple-scanned-docs.json");
        var scannedDocuments = asList(
            getScannedDocument("1000"),
            getScannedDocument("2000")
        );
        var exceptionRecord = new ExceptionRecord(
            EXCEPTION_REFERENCE,
            "caseTypeId",
            "envelopeId123",
            false,
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
        ScannedDocumentsHelper.setExceptionRecordIdToScannedDocuments(exceptionRecord, caseDetails);

        //then
        var updatedScannedDocuments = getScannedDocuments(caseDetails);
        assertThat(updatedScannedDocuments).hasSize(3);
        assertThat(updatedScannedDocuments.get(0).controlNumber).isEqualTo("1000");
        assertThat(updatedScannedDocuments.get(0).exceptionReference).isEqualTo("1");
        assertThat(updatedScannedDocuments.get(1).controlNumber).isEqualTo("2000");
        assertThat(updatedScannedDocuments.get(1).exceptionReference).isEqualTo("1");
        // not present in the exception record and should not be set
        assertThat(updatedScannedDocuments.get(2).controlNumber).isEqualTo("3000");
        assertThat(updatedScannedDocuments.get(2).exceptionReference).isNull();
    }

    @Test
    void setExceptionRecordIdToScannedDocuments_should_handle_empty_scanned_documents_in_exception() throws Exception {
        // given
        //contains documents with control numbers 1000, 2000, 3000
        var caseDetails = getCaseUpdateDetails("case-data/multiple-scanned-docs.json");
        var exceptionRecord = new ExceptionRecord(
            EXCEPTION_REFERENCE,
            "caseTypeId",
            "envelopeId123",
            false,
            "poBox",
            "poBoxJurisdiction",
            Classification.EXCEPTION,
            "formType",
            now(),
            now(),
            emptyList(),
            emptyList()
        );

        // when
        ScannedDocumentsHelper.setExceptionRecordIdToScannedDocuments(exceptionRecord, caseDetails);

        //then
        var updatedScannedDocuments = getScannedDocuments(caseDetails);
        assertThat(updatedScannedDocuments).hasSize(3);
        assertThat(updatedScannedDocuments.get(0).controlNumber).isEqualTo("1000");
        assertThat(updatedScannedDocuments.get(0).exceptionReference).isNull();
        assertThat(updatedScannedDocuments.get(1).controlNumber).isEqualTo("2000");
        assertThat(updatedScannedDocuments.get(1).exceptionReference).isNull();
        assertThat(updatedScannedDocuments.get(2).controlNumber).isEqualTo("3000");
        assertThat(updatedScannedDocuments.get(2).exceptionReference).isNull();
    }

    private ScannedDocument getScannedDocument(String controlNumber) {
        return new ScannedDocument(
            FORM,
            "subtype",
            new DocumentUrl("url", "binaryUrl", "fileName"),
            controlNumber,
            "fileName",
            now(),
            now()
        );
    }

    private CaseDetails getCaseDetails(String resourceName) throws IOException {
        return objectMapper.readValue(fileContentAsBytes(resourceName), CaseDetails.class);
    }

    private CaseUpdateDetails getCaseUpdateDetails(String resourceName) throws IOException {
        return objectMapper.readValue(fileContentAsBytes(resourceName), CaseUpdateDetails.class);
    }

    private List<uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.ScannedDocument> getScannedDocuments(
        CaseUpdateDetails caseDetails
    ) {
        var caseData = (Map<String, Object>) caseDetails.caseData;
        var scannedDocuments = (List<Map<String, Object>>) caseData.get(SCANNED_DOCUMENTS);
        return scannedDocuments.stream().map(o ->
            objectMapper.convertValue(
                o.get("value"),
                uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.ScannedDocument.class
            )
        ).collect(Collectors.toList());
    }
}
