package uk.gov.hmcts.reform.bulkscan.orchestrator.helper;

import org.junit.Test;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Document;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.fileContentAsBytes;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.objectMapper;

public class ScannedDocumentsHelperTest {

    @Test
    public void getDocuments_should_extract_all_documents() throws Exception {
        CaseDetails caseDetails = getCaseDetails("case-data/multiple-scanned-docs.json");

        List<Document> documents = ScannedDocumentsHelper.getDocuments(caseDetails);

        assertThat(documents)
            .extracting(doc -> doc.controlNumber)
            .containsExactlyInAnyOrder("1000", "2000", "3000");
    }

    @Test
    public void getDocuments_should_return_empty_collection_when_scannedDocuments_are_missing()
        throws Exception {
        CaseDetails caseDetails = getCaseDetails("case-data/missing-scanned-documents.json");

        List<Document> documents = ScannedDocumentsHelper.getDocuments(caseDetails);

        assertThat(documents).isEmpty();
    }

    @Test
    public void getDocuments_should_map_document_properties_correctly() throws Exception {
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
            "https://doc-url-1.example.com"
        );

        assertThat(document).isEqualToComparingFieldByField(expectedDocument);
    }

    @Test
    public void should_handle_null_fields_in_document() throws Exception {
        // given
        CaseDetails caseDetails = getCaseDetails("case-data/null-fields-in-doc.json");

        // when
        List<Document> documents = ScannedDocumentsHelper.getDocuments(caseDetails);

        // then
        assertThat(documents).hasSize(1);
        assertThat(documents.get(0))
            .isEqualToComparingFieldByField(
                new Document(null, null, null, null, null, null)
            );
    }

    @Test
    public void should_handle_null_document() throws Exception {
        // given
        CaseDetails caseDetails = getCaseDetails("case-data/null-doc.json");

        // when
        List<Document> documents = ScannedDocumentsHelper.getDocuments(caseDetails);

        // then
        assertThat(documents).hasSize(1);
        assertThat(documents.get(0)).isNull();
    }

    private CaseDetails getCaseDetails(String resourceName) throws IOException {
        return objectMapper.readValue(fileContentAsBytes(resourceName), CaseDetails.class);
    }
}
