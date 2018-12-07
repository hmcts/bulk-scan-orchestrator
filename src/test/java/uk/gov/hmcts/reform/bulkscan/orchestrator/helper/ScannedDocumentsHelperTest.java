package uk.gov.hmcts.reform.bulkscan.orchestrator.helper;

import com.google.common.collect.ImmutableMap;
import org.junit.Test;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.CcdCollectionElement;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.CcdKeyValue;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Document;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toMap;
import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.fileContentAsBytes;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.objectMapper;

public class ScannedDocumentsHelperTest {

    @Test
    public void getDocuments_should_extract_all_documents() throws Exception {
        CaseDetails caseDetails = getCaseDetails("case-data/multiple-scanned-docs.json");

        List<Document> documents = ScannedDocumentsHelper.getDocuments(caseDetails);

        assertThat(documents)
            .extracting("controlNumber")
            .containsExactlyInAnyOrder("1000", "2000", "3000");
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
            Instant.parse("2018-12-01T12:34:56.123Z"),
            "https://doc-url-1.example.com",
            ImmutableMap.of(
                "field1", "value1",
                "field2", "value2"
            )
        );

        assertThat(document).isEqualToComparingFieldByField(expectedDocument);
    }

    @Test
    public void getDocuments_should_handle_missing_ocr_data() throws Exception {
        CaseDetails caseDetails = getCaseDetails("case-data/no-ocr-data.json");

        List<Document> documents = ScannedDocumentsHelper.getDocuments(caseDetails);

        assertThat(documents.size()).isOne();
        assertThat(documents.get(0).ocrData).isNull();
    }

    @Test
    public void mapOcrDataToCcdFormat_should_map_fields_correctly() {
        Map<String, String> originalData = ImmutableMap.of(
            "field1", "value1",
            "field2", "value2",
            "field3", "value3"
        );

        List<CcdCollectionElement<CcdKeyValue>> ccdOcrData =
            ScannedDocumentsHelper.mapOcrDataToCcdFormat(originalData);

        assertThat(ccdOcrData.size()).isEqualTo(originalData.size());

        Map<String, String> ccdOcrDataAsMap =
            ccdOcrData
                .stream()
                .map(element -> element.value)
                .collect(toMap(kv -> kv.key, kv -> kv.value));

        assertThat(ccdOcrDataAsMap).isEqualTo(originalData);
    }

    @Test
    public void mapOcrDataToCcdFormat_should_return_null_when_ocr_data_is_null() {
        assertThat(ScannedDocumentsHelper.mapOcrDataToCcdFormat(null)).isNull();
    }

    private CaseDetails getCaseDetails(String resourceName) throws IOException {
        return objectMapper.readValue(fileContentAsBytes(resourceName), CaseDetails.class);
    }
}
