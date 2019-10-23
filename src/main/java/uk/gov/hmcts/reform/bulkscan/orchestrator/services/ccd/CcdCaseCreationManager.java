package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import com.google.common.collect.ImmutableMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.request.ExceptionRecord;
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.ServiceConfigItem;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.callback.ProcessResult;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.YesNoFieldValues;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.util.List;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.joining;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.ExceptionRecordFields.CASE_REFERENCE;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.ExceptionRecordFields.DISPLAY_WARNINGS;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.ExceptionRecordFields.OCR_DATA_VALIDATION_WARNINGS;

@Component
public class CcdCaseCreationManager {
    private static final Logger log = LoggerFactory.getLogger(CcdCaseCreationManager.class);

    private final CcdCaseSubmitter ccdCaseSubmitter;
    private final CcdApi ccdApi;

    public CcdCaseCreationManager(
        CcdCaseSubmitter ccdCaseSubmitter,
        CcdApi ccdApi
    ) {
        this.ccdCaseSubmitter = ccdCaseSubmitter;
        this.ccdApi = ccdApi;
    }

    public ProcessResult tryCreateNewCase(
        ExceptionRecord exceptionRecord,
        ServiceConfigItem configItem,
        boolean ignoreWarnings,
        String idamToken,
        String userId,
        CaseDetails exceptionRecordData
    ) {
        log.debug("Creating case for exception record id {}", exceptionRecord.id);

        List<Long> ids = ccdApi.getCaseRefsByBulkScanCaseReference(exceptionRecord.id, configItem.getService());
        if (ids.isEmpty()) {
            return ccdCaseSubmitter.createNewCase(
                exceptionRecord,
                configItem,
                ignoreWarnings,
                idamToken,
                userId,
                exceptionRecordData
            );
        } else if (ids.size() == 1) {
            return new ProcessResult(
                ImmutableMap.<String, Object>builder()
                    .put(CASE_REFERENCE, Long.toString(ids.get(0)))
                    .put(DISPLAY_WARNINGS, YesNoFieldValues.NO)
                    .put(OCR_DATA_VALIDATION_WARNINGS, emptyList())
                    .build()
            );
        } else {
            return new ProcessResult(
                emptyList(),
                singletonList(
                    String.format(
                        "Multiple cases (%s) found for the given bulk scan case reference: %s",
                        ids.stream().map(String::valueOf).collect(joining(", ")),
                        exceptionRecord.id
                    )
                )
            );
        }
    }
}
