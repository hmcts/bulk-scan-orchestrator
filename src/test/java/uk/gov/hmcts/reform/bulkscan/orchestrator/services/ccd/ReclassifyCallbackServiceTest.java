package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.callback.ProcessResult;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class ReclassifyCallbackServiceTest {

    private static final String NEW_APPLICATION_CLASSIFICATION = "NEW_APPLICATION";
    private static final String JOURNEY_CLASSIFICATION_FIELD_NAME = "journeyClassification";

    private ReclassifyCallbackService reclassifyCallbackService;

    @BeforeEach
    void setUp() {
        this.reclassifyCallbackService = new ReclassifyCallbackService();
    }

    @Test
    void should_update_classification_when_exception_record_data_is_valid() {
        Map<String, Object> originalFields = ImmutableMap.<String, Object>builder()
            .put("field1", 123)
            .put(JOURNEY_CLASSIFICATION_FIELD_NAME, NEW_APPLICATION_CLASSIFICATION)
            .put("field2", new Object())
            .build();

        ProcessResult result = reclassifyCallbackService.reclassifyExceptionRecord(
            caseDetails(originalFields),
            "user1"
        );

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getWarnings()).isEmpty();

        Map<String, Object> updatedFields = result.getExceptionRecordData();
        assertThat(updatedFields).hasSize(3);
        assertThat(updatedFields.get(JOURNEY_CLASSIFICATION_FIELD_NAME)).isEqualTo("SUPPLEMENTARY_EVIDENCE_WITH_OCR");
        assertThat(updatedFields.get("field1")).isEqualTo(originalFields.get("field1"));
        assertThat(updatedFields.get("field2")).isEqualTo(originalFields.get("field2"));
    }

    @Test
    void should_return_error_when_classification_is_not_new_application() {
        String invalidClassification = "SUPPLEMENTARY_EVIDENCE";

        ProcessResult result = reclassifyCallbackService.reclassifyExceptionRecord(
            caseDetails(invalidClassification),
            "userId1"
        );

        assertThat(result.getErrors()).containsExactly(
            expectedErrorForInvalidClassification(invalidClassification)
        );

        assertThat(result.getWarnings()).isEmpty();
        assertThat(result.getExceptionRecordData()).isEmpty();
    }

    private String expectedErrorForInvalidClassification(String classification) {
        return String.format(
            "This exception record's journey classification is '%s'. "
                + "Reclassification is only possible from 'NEW_APPLICATION' classification.",
            classification
        );
    }

    private String expectedErrorForInvalidState(String state) {
        return String.format(
            "Reclassification is only allowed for 'ScannedRecordReceived' state. "
                + "This Exception record's current state is '%s'.",
            state
        );
    }

    private CaseDetails caseDetails(String classification) {
        return caseDetails(
            ImmutableMap.of(JOURNEY_CLASSIFICATION_FIELD_NAME, classification)
        );
    }

    private CaseDetails caseDetails(Map<String, Object> fields) {
        return CaseDetails
            .builder()
            .id(123L)
            .state("ScannedRecordReceived")
            .data(fields)
            .build();
    }
}
