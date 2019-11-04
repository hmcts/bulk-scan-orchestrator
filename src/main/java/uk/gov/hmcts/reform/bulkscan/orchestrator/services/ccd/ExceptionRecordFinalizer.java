package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.YesNoFieldValues;

import java.util.Map;

import static java.util.Collections.emptyList;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.ExceptionRecordFields.CASE_REFERENCE;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.ExceptionRecordFields.DISPLAY_WARNINGS;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.ExceptionRecordFields.OCR_DATA_VALIDATION_WARNINGS;

@Service
public class ExceptionRecordFinalizer {

    public Map<String, Object> finalizeExceptionRecord(
        Map<String, Object> originalFields,
        Long caseReference
    ) {
        Map<String, Object> fieldsToUpdate =
            ImmutableMap.<String, Object>builder()
                .put(CASE_REFERENCE, Long.toString(caseReference))
                .put(DISPLAY_WARNINGS, YesNoFieldValues.NO)
                .put(OCR_DATA_VALIDATION_WARNINGS, emptyList())
                .build();

        return ImmutableMap.<String, Object>builder()
            .putAll(Maps.difference(originalFields, fieldsToUpdate).entriesOnlyOnLeft())
            .putAll(fieldsToUpdate)
            .build();
    }
}
