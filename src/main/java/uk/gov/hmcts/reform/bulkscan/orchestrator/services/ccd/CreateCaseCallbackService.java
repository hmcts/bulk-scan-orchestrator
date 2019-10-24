package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import io.vavr.collection.Seq;
import io.vavr.control.Try;
import io.vavr.control.Validation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.request.ExceptionRecord;
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.ServiceConfigItem;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.in.CcdCallbackRequest;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.callback.CreateCaseValidator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.callback.ProcessResult;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.YesNoFieldValues;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.config.ServiceConfigProvider;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.util.List;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.joining;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CallbackValidations.hasServiceNameInCaseTypeId;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.EventIdValidator.isCreateNewCaseEvent;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.ExceptionRecordFields.CASE_REFERENCE;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.ExceptionRecordFields.DISPLAY_WARNINGS;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.ExceptionRecordFields.OCR_DATA_VALIDATION_WARNINGS;

@Service
public class CreateCaseCallbackService {

    private static final Logger log = LoggerFactory.getLogger(CreateCaseCallbackService.class);

    private static final String EXCEPTION_RECORD_REFERENCE = "bulkScanCaseReference";

    private final CreateCaseValidator validator;
    private final ServiceConfigProvider serviceConfigProvider;
    private final CcdCaseSubmitter ccdCaseSubmitter;
    private final CcdApi ccdApi;

    public CreateCaseCallbackService(
        CreateCaseValidator validator,
        ServiceConfigProvider serviceConfigProvider,
        CcdCaseSubmitter ccdCaseSubmitter,
        CcdApi ccdApi
    ) {
        this.validator = validator;
        this.serviceConfigProvider = serviceConfigProvider;
        this.ccdCaseSubmitter = ccdCaseSubmitter;
        this.ccdApi = ccdApi;
    }

    /**
     * Create case record from exception case record.
     *
     * @return Either list of errors or map of changes - new case reference
     */
    public ProcessResult process(CcdCallbackRequest request, String idamToken, String userId) {
        Validation<String, Void> canAccess = assertAllowToAccess(request.getCaseDetails(), request.getEventId());

        if (canAccess.isInvalid()) {
            // log happens in assertion method
            return new ProcessResult(emptyList(), singletonList(canAccess.getError()));
        }

        // already validated in mandatory section ^
        ServiceConfigItem serviceConfigItem = getServiceConfig(request.getCaseDetails()).get();

        CaseDetails exceptionRecordData = request.getCaseDetails();

        ProcessResult result = validator
            .getValidation(exceptionRecordData)
            .map(exceptionRecord -> tryCreateNewCase(
                exceptionRecord,
                serviceConfigItem,
                request.isIgnoreWarnings(),
                idamToken,
                userId,
                exceptionRecordData
            ))
            .mapError(Seq::asJava)
            .getOrElseGet(errors -> new ProcessResult(emptyList(), errors));

        if (!result.getWarnings().isEmpty()) {
            log.warn(
                "Warnings found for {} during callback process:\n  - {}",
                serviceConfigItem.getService(),
                String.join("\n  - ", result.getWarnings())
            );
        }

        if (!result.getErrors().isEmpty()) {
            log.error(
                "Errors found for {} during callback process:\n  - {}",
                serviceConfigItem.getService(),
                String.join("\n  - ", result.getErrors())
            );
        }

        return result;
    }

    private Validation<String, Void> assertAllowToAccess(CaseDetails caseDetails, String eventId) {
        return validator.mandatoryPrerequisites(
            () -> isCreateNewCaseEvent(eventId),
            () -> getServiceConfig(caseDetails).map(item -> null)
        );
    }

    private Validation<String, ServiceConfigItem> getServiceConfig(CaseDetails caseDetails) {
        return hasServiceNameInCaseTypeId(caseDetails).flatMap(service -> Try
            .of(() -> serviceConfigProvider.getConfig(service))
            .toValidation()
            .mapError(Throwable::getMessage)
        )
            .filter(item -> !Strings.isNullOrEmpty(item.getTransformationUrl()))
            .getOrElse(Validation.invalid("Transformation URL is not configured"));
    }

    private ProcessResult tryCreateNewCase(
        ExceptionRecord exceptionRecord,
        ServiceConfigItem configItem,
        boolean ignoreWarnings,
        String idamToken,
        String userId,
        CaseDetails exceptionRecordData
    ) {
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
