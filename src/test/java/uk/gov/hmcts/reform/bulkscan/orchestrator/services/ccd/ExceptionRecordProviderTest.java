package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.ExceptionRecordFields;

import java.util.HashMap;
import java.util.Map;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.ExceptionRecordFields.DISPLAY_WARNINGS;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.ExceptionRecordFields.OCR_DATA_VALIDATION_WARNINGS;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.YesNoFieldValues.NO;

class ExceptionRecordProviderTest {

    public static final long CASE = 100L;
    private ExceptionRecordProvider exceptionRecordProvider;

    @BeforeEach
    void setUp() {
        exceptionRecordProvider = new ExceptionRecordProvider();
    }

    @Test
    void should_add_new_values() {
        // given
        Map<String, Object> originalValues = new HashMap<>();
        originalValues.put("field1", "value1");

        // when
        Map<String,Object> res = exceptionRecordProvider.prepareResultExceptionRecord(originalValues, CASE);

        // then
        assertThat(res.get("field1")).isEqualTo("value1");
        assertThat(res.get(ExceptionRecordFields.CASE_REFERENCE)).isEqualTo(Long.toString(CASE));
        assertThat(res.get(DISPLAY_WARNINGS)).isEqualTo(NO);
        assertThat(res.get(OCR_DATA_VALIDATION_WARNINGS)).isEqualTo(emptyList());
    }

    @Test
    void should_replace_values() {
        // given
        Map<String, Object> originalValues = new HashMap<>();
        originalValues.put("field1", "value1");
        originalValues.put(ExceptionRecordFields.CASE_REFERENCE, "value2");
        originalValues.put(DISPLAY_WARNINGS, "value3");
        originalValues.put(OCR_DATA_VALIDATION_WARNINGS, asList("value4"));

        // when
        Map<String,Object> res = exceptionRecordProvider.prepareResultExceptionRecord(originalValues, CASE);

        // then
        assertThat(res.get("field1")).isEqualTo("value1");
        assertThat(res.get(ExceptionRecordFields.CASE_REFERENCE)).isEqualTo(Long.toString(CASE));
        assertThat(res.get(DISPLAY_WARNINGS)).isEqualTo(NO);
        assertThat(res.get(OCR_DATA_VALIDATION_WARNINGS)).isEqualTo(emptyList());
    }
}
