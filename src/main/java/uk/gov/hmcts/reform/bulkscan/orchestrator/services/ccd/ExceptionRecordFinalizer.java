package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import com.google.common.collect.ImmutableMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.YesNoFieldValues;

import java.util.HashMap;
import java.util.Map;

import static java.util.Collections.emptyList;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.ExceptionRecordFields.CASE_REFERENCE;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.ExceptionRecordFields.DISPLAY_WARNINGS;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.ExceptionRecordFields.OCR_DATA_VALIDATION_WARNINGS;

@Service
public class ExceptionRecordFinalizer {
    private static final Logger log = LoggerFactory.getLogger(ExceptionRecordFinalizer.class);

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

        Map<String, Object> finalizedMap = new HashMap<>();
        for (String key: originalFields.keySet()) {
            finalizedMap.put(key, originalFields.get(key));
        }
        finalizedMap.putAll(fieldsToUpdate);

        return finalizedMap;
    }
}
