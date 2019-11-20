package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import com.google.common.base.Strings;
import io.vavr.collection.Seq;
import io.vavr.control.Try;
import io.vavr.control.Validation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.request.ExceptionRecord;
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.ServiceConfigItem;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.in.CcdCallbackRequest;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.callback.CallbackException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.callback.CreateCaseValidator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.callback.ProcessResult;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.YesNoFieldValues;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.config.ServiceConfigProvider;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.joining;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CallbackValidations.hasIdamToken;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CallbackValidations.hasServiceNameInCaseTypeId;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CallbackValidations.hasUserId;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.EventIdValidator.isCreateNewCaseEvent;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.ExceptionRecordFields.AWAITING_PAYMENT_DCN_PROCESSING;

@Service
public class CreateCaseCallbackService {

    private static final Logger log = LoggerFactory.getLogger(CreateCaseCallbackService.class);

    public static final String AWAITING_PAYMENTS_MESSAGE =
        "Payments for this Exception Record have not been processed yet";

    private final CreateCaseValidator validator;
    private final ServiceConfigProvider serviceConfigProvider;
    private final CcdApi ccdApi;
    private final CcdNewCaseCreator ccdNewCaseCreator;
    private final ExceptionRecordFinalizer exceptionRecordFinalizer;

    public CreateCaseCallbackService(
        CreateCaseValidator validator,
        ServiceConfigProvider serviceConfigProvider,
        CcdApi ccdApi,
        CcdNewCaseCreator ccdNewCaseCreator,
        ExceptionRecordFinalizer exceptionRecordFinalizer
    ) {
        this.validator = validator;
        this.serviceConfigProvider = serviceConfigProvider;
        this.ccdApi = ccdApi;
        this.ccdNewCaseCreator = ccdNewCaseCreator;
        this.exceptionRecordFinalizer = exceptionRecordFinalizer;
    }

    /**
     * Create case record from exception case record.
     *
     * @return ProcessResult map of changes or list of errors/warnings
     */
    public ProcessResult process(CcdCallbackRequest request, String idamToken, String userId) {
        Validation<String, Void> canAccess = assertAllowToAccess(
            request.getCaseDetails(),
            request.getEventId(),
            idamToken,
            userId
        );

        if (canAccess.isInvalid()) {
            log.warn("Validation error: {}", canAccess.getError());

            throw new CallbackException(canAccess.getError());
        }

        // already validated in mandatory section ^
        ServiceConfigItem serviceConfigItem = getServiceConfig(request.getCaseDetails()).get();

        CaseDetails exceptionRecordData = request.getCaseDetails();

        // Extract exception record ID for logging reasons
        String exceptionRecordId = validator.getCaseId(exceptionRecordData).getOrElse("UNKNOWN");

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
                "Warnings found for {} exception record {} during callback process: {}",
                serviceConfigItem.getService(),
                exceptionRecordId,
                result.getWarnings().size()
            );
        }

        if (!result.getErrors().isEmpty()) {
            // no need to error - it's informational log. specific logs will be error'ed already
            log.warn(
                "Errors found for {} exception record {} during callback process: {}",
                serviceConfigItem.getService(),
                exceptionRecordId,
                result.getErrors().size()
            );
        }

        return result;
    }

    private Validation<String, Void> assertAllowToAccess(
        CaseDetails caseDetails,
        String eventId,
        String idamToken,
        String userId
    ) {
        return validator.mandatoryPrerequisites(
            () -> isCreateNewCaseEvent(eventId),
            () -> getServiceConfig(caseDetails).map(item -> null),
            () -> hasIdamToken(idamToken).map(item -> null),
            () -> hasUserId(userId).map(item -> null)
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
        boolean awaitsPaymentProcessing = Optional
            .ofNullable(exceptionRecordData.getData().get(AWAITING_PAYMENT_DCN_PROCESSING))
            .map(awaiting -> awaiting.toString().equals(YesNoFieldValues.YES))
            .orElse(false);

        if (awaitsPaymentProcessing && configItem.allowCreatingCaseBeforePaymentsAreProcessed() && !ignoreWarnings) {
            return new ProcessResult(singletonList(AWAITING_PAYMENTS_MESSAGE), emptyList());
        } else if (awaitsPaymentProcessing && !configItem.allowCreatingCaseBeforePaymentsAreProcessed()) {
            return new ProcessResult(emptyList(), singletonList(AWAITING_PAYMENTS_MESSAGE));
        } else {
            List<Long> ids = ccdApi.getCaseRefsByBulkScanCaseReference(exceptionRecord.id, configItem.getService());
            if (ids.isEmpty()) {
                return ccdNewCaseCreator.createNewCase(
                    exceptionRecord,
                    configItem,
                    ignoreWarnings,
                    idamToken,
                    userId,
                    exceptionRecordData
                );
            } else if (ids.size() == 1) {
                final Map<String, Object> finalizedExceptionRecordData =
                    exceptionRecordFinalizer.finalizeExceptionRecord(
                        exceptionRecordData.getData(),
                        ids.get(0)
                    );
                return new ProcessResult(finalizedExceptionRecordData);
            } else {
                String msg = String.format(
                    "Multiple cases (%s) found for the given bulk scan case reference: %s",
                    ids.stream().map(String::valueOf).collect(joining(", ")),
                    exceptionRecord.id
                );
                log.error(msg);
                throw new MultipleCasesFoundException(msg);
            }
        }
    }
}
