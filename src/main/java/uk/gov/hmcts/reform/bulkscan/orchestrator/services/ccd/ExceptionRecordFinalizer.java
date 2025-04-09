package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import com.google.common.collect.ImmutableMap;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.bulkscan.orchestrator.errorhandling.exceptions.CallbackException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.YesNoFieldValues;

import java.util.HashMap;
import java.util.Map;

import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.ExceptionRecordFields.ATTACH_TO_CASE_REFERENCE;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.ExceptionRecordFields.CASE_REFERENCE;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.ExceptionRecordFields.DISPLAY_WARNINGS;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.ExceptionRecordFields.OCR_DATA_VALIDATION_WARNINGS;

@Service
public class ExceptionRecordFinalizer {

    public Map<String, Object> finalizeExceptionRecord(
        Map<String, Object> originalFields,
        String caseReference,
        CcdCallbackType callbackType
    ) {
        Map<String, Object> fieldsToUpdate =
            ImmutableMap.<String, Object>builder()
                .put(getCaseReferenceFieldName(callbackType), caseReference)
                .put(DISPLAY_WARNINGS, YesNoFieldValues.NO)
                .put(OCR_DATA_VALIDATION_WARNINGS, emptyList())
                .build();

        Map<String, Object> finalizedMap = new HashMap<>();
        for (Map.Entry<String, Object> entry : originalFields.entrySet()) {
            if (!fieldsToUpdate.containsKey(entry.getKey())) {
                finalizedMap.put(entry.getKey(), entry.getValue());
            }
        }
        finalizedMap.putAll(fieldsToUpdate);

        return finalizedMap;
    }

    private String getCaseReferenceFieldName(CcdCallbackType callbackType) {
        switch (callbackType) {
            case CASE_CREATION:
                return CASE_REFERENCE;
            case ATTACHING_SUPPLEMENTARY_EVIDENCE:
                return ATTACH_TO_CASE_REFERENCE;
            default:
                throw new CallbackException(format("Unrecognised CCD callback type: %s", callbackType));
        }
    }
}
