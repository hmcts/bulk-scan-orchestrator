package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.YesNoFieldValues.NO;

class ExceptionRecordFinalizerTest {

    private static final String CASE_ID = "100";
    private static final String FIELD_1 = "field1";

    public static final String FIELD_CASE_REFERENCE = "caseReference";
    public static final String FIELD_DISPLAY_WARNINGS = "displayWarnings";
    public static final String FIELD_OCR_DATA_VALIDATION_WARNINGS = "ocrDataValidationWarnings";
    public static final String FIELD_ATTACH_TO_CASE_REFERENCE = "attachToCaseReference";

    private ExceptionRecordFinalizer exceptionRecordFinalizer;

    @BeforeEach
    void setUp() {
        exceptionRecordFinalizer = new ExceptionRecordFinalizer();
    }

    @Test
    void should_add_new_values() {
        // given
        Map<String, Object> originalValues = new HashMap<>();
        originalValues.put("field1", "value1");

        // when
        Map<String, Object> res = exceptionRecordFinalizer.finalizeExceptionRecord(
            originalValues,
            CASE_ID,
            CcdCallbackType.CASE_CREATION
        );

        // then
        assertThat(res).containsOnlyKeys(
                "field1",
                FIELD_CASE_REFERENCE,
                FIELD_DISPLAY_WARNINGS,
                FIELD_OCR_DATA_VALIDATION_WARNINGS
        )
                .containsEntry("field1", "value1")
                .containsEntry(FIELD_CASE_REFERENCE, CASE_ID)
                .containsEntry(FIELD_DISPLAY_WARNINGS, NO)
                .containsEntry(FIELD_OCR_DATA_VALIDATION_WARNINGS, emptyList());
    }

    @Test
    void should_handle_null_values() {
        // given
        Map<String, Object> originalValues = new HashMap<>();
        originalValues.put("field1", "value1");
        originalValues.put("field2", null);

        // when
        Map<String, Object> res = exceptionRecordFinalizer.finalizeExceptionRecord(
            originalValues,
            CASE_ID,
            CcdCallbackType.CASE_CREATION
        );

        // then
        assertThat(res).containsOnlyKeys(
                "field1",
                "field2",
                FIELD_CASE_REFERENCE,
                FIELD_DISPLAY_WARNINGS,
                FIELD_OCR_DATA_VALIDATION_WARNINGS
        )
                .containsEntry("field1", "value1")
                .containsEntry("field2", null)
                .containsEntry(FIELD_CASE_REFERENCE, CASE_ID)
                .containsEntry(FIELD_DISPLAY_WARNINGS, NO)
                .containsEntry(FIELD_OCR_DATA_VALIDATION_WARNINGS, emptyList());
    }

    @Test
    void should_replace_values() {
        // given
        Map<String, Object> originalValues = new HashMap<>();
        originalValues.put(FIELD_1, "value1");
        originalValues.put(FIELD_CASE_REFERENCE, "value2");
        originalValues.put(FIELD_DISPLAY_WARNINGS, "value3");
        originalValues.put(FIELD_OCR_DATA_VALIDATION_WARNINGS, singletonList("value4"));

        // when
        Map<String, Object> res = exceptionRecordFinalizer.finalizeExceptionRecord(
            originalValues,
            CASE_ID,
            CcdCallbackType.CASE_CREATION
        );

        // then
        assertThat(res).containsOnlyKeys(
                "field1",
                FIELD_CASE_REFERENCE,
                FIELD_DISPLAY_WARNINGS,
                FIELD_OCR_DATA_VALIDATION_WARNINGS
        )
                .containsEntry("field1", "value1")
                .containsEntry(FIELD_CASE_REFERENCE, CASE_ID)
                .containsEntry(FIELD_DISPLAY_WARNINGS, NO)
                .containsEntry(FIELD_OCR_DATA_VALIDATION_WARNINGS, emptyList());
    }

    @Test
    void should_set_case_reference_when_creating_case() {
        // given
        Map<String, Object> originalValues = ImmutableMap.of("field1", "value1");

        // when
        Map<String, Object> res = exceptionRecordFinalizer.finalizeExceptionRecord(
            originalValues,
            CASE_ID,
            CcdCallbackType.CASE_CREATION
        );

        // then
        assertThat(res)
                .containsEntry(FIELD_CASE_REFERENCE, CASE_ID)
                .doesNotContainKeys(FIELD_ATTACH_TO_CASE_REFERENCE);
    }

    @Test
    void should_set_attach_to_case_reference_when_attaching_supplementary_evidence() {
        // given
        Map<String, Object> originalValues = ImmutableMap.of("field1", "value1");

        // when
        Map<String, Object> res = exceptionRecordFinalizer.finalizeExceptionRecord(
            originalValues,
            CASE_ID,
            CcdCallbackType.ATTACHING_SUPPLEMENTARY_EVIDENCE
        );

        // then
        assertThat(res)
                .containsEntry(FIELD_ATTACH_TO_CASE_REFERENCE, CASE_ID)
                .doesNotContainKeys(FIELD_CASE_REFERENCE);
    }
}
