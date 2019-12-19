package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.YesNoFieldValues.NO;

class ExceptionRecordFinalizerTest {

    private static final long CASE_ID = 100L;
    private static final String FIELD_1 = "field1";

    public static final String CASE_REFERENCE = "caseReference";
    public static final String DISPLAY_WARNINGS = "displayWarnings";
    public static final String OCR_DATA_VALIDATION_WARNINGS = "ocrDataValidationWarnings";
    public static final String ATTACH_TO_CASE_REFERENCE = "attachToCaseReference";

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
        assertThat(res).containsOnlyKeys("field1", CASE_REFERENCE, DISPLAY_WARNINGS, OCR_DATA_VALIDATION_WARNINGS);
        assertThat(res.get("field1")).isEqualTo("value1");
        assertThat(res.get(CASE_REFERENCE)).isEqualTo(Long.toString(CASE_ID));
        assertThat(res.get(DISPLAY_WARNINGS)).isEqualTo(NO);
        assertThat(res.get(OCR_DATA_VALIDATION_WARNINGS)).isEqualTo(emptyList());
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
            CASE_REFERENCE,
            DISPLAY_WARNINGS,
            OCR_DATA_VALIDATION_WARNINGS
        );
        assertThat(res.get("field1")).isEqualTo("value1");
        assertThat(res.get("field2")).isEqualTo(null);
        assertThat(res.get(CASE_REFERENCE)).isEqualTo(Long.toString(CASE_ID));
        assertThat(res.get(DISPLAY_WARNINGS)).isEqualTo(NO);
        assertThat(res.get(OCR_DATA_VALIDATION_WARNINGS)).isEqualTo(emptyList());
    }

    @Test
    void should_replace_values() {
        // given
        Map<String, Object> originalValues = new HashMap<>();
        originalValues.put(FIELD_1, "value1");
        originalValues.put(CASE_REFERENCE, "value2");
        originalValues.put(DISPLAY_WARNINGS, "value3");
        originalValues.put(OCR_DATA_VALIDATION_WARNINGS, asList("value4"));

        // when
        Map<String, Object> res = exceptionRecordFinalizer.finalizeExceptionRecord(
            originalValues,
            CASE_ID,
            CcdCallbackType.CASE_CREATION
        );

        // then
        assertThat(res).containsOnlyKeys("field1", CASE_REFERENCE, DISPLAY_WARNINGS, OCR_DATA_VALIDATION_WARNINGS);
        assertThat(res.get("field1")).isEqualTo("value1");
        assertThat(res.get(CASE_REFERENCE)).isEqualTo(Long.toString(CASE_ID));
        assertThat(res.get(DISPLAY_WARNINGS)).isEqualTo(NO);
        assertThat(res.get(OCR_DATA_VALIDATION_WARNINGS)).isEqualTo(emptyList());
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
        assertThat(res.get(CASE_REFERENCE)).isEqualTo(Long.toString(CASE_ID));
        assertThat(res).doesNotContainKeys(ATTACH_TO_CASE_REFERENCE);
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
        assertThat(res.get(ATTACH_TO_CASE_REFERENCE)).isEqualTo(Long.toString(CASE_ID));
        assertThat(res).doesNotContainKeys(CASE_REFERENCE);
    }
}
