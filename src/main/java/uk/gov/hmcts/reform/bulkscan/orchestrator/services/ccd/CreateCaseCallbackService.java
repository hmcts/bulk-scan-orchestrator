package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import com.google.common.base.Strings;
import io.vavr.collection.Seq;
import io.vavr.control.Try;
import io.vavr.control.Validation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.ServiceConfigItem;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.in.CcdCallbackRequest;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.internal.ExceptionRecord;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.callback.CallbackException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.callback.CreateCaseResult;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.callback.ExceptionRecordValidator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.callback.PaymentsHelper;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.callback.ProcessResult;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.YesNoFieldValues;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.config.ServiceConfigProvider;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.payments.PaymentsPublishingException;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.util.List;
import java.util.Optional;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.joining;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.data.callbackresult.NewCallbackResult.createCaseRequest;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CallbackValidations.hasIdamToken;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CallbackValidations.hasServiceNameInCaseTypeId;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CallbackValidations.hasUserId;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.ExceptionRecordFields.AWAITING_PAYMENT_DCN_PROCESSING;

@Service
public class CreateCaseCallbackService {

    private static final Logger log = LoggerFactory.getLogger(CreateCaseCallbackService.class);

    public static final String AWAITING_PAYMENTS_MESSAGE =
        "Payments for this Exception Record have not been processed yet";

    private final ExceptionRecordValidator exceptionRecordValidator;
    private final ServiceConfigProvider serviceConfigProvider;
    private final CaseFinder caseFinder;
    private final CcdNewCaseCreator ccdNewCaseCreator;
    private final ExceptionRecordFinalizer exceptionRecordFinalizer;
    private final PaymentsProcessor paymentsProcessor;
    private final CallbackResultRepositoryProxy callbackResultRepositoryProxy;
    private final EventIdValidator eventIdValidator;

    public CreateCaseCallbackService(
            ExceptionRecordValidator exceptionRecordValidator,
            ServiceConfigProvider serviceConfigProvider,
            CaseFinder caseFinder,
            CcdNewCaseCreator ccdNewCaseCreator,
            ExceptionRecordFinalizer exceptionRecordFinalizer,
            PaymentsProcessor paymentsProcessor,
            CallbackResultRepositoryProxy callbackResultRepositoryProxy,
            EventIdValidator eventIdValidator
    ) {
        this.exceptionRecordValidator = exceptionRecordValidator;
        this.serviceConfigProvider = serviceConfigProvider;
        this.caseFinder = caseFinder;
        this.ccdNewCaseCreator = ccdNewCaseCreator;
        this.exceptionRecordFinalizer = exceptionRecordFinalizer;
        this.paymentsProcessor = paymentsProcessor;
        this.callbackResultRepositoryProxy = callbackResultRepositoryProxy;
        this.eventIdValidator = eventIdValidator;
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
        String exceptionRecordId = exceptionRecordValidator.getCaseId(exceptionRecordData).getOrElse("UNKNOWN");

        ProcessResult result = exceptionRecordValidator
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
        return exceptionRecordValidator.mandatoryPrerequisites(
            () -> eventIdValidator.isCreateNewCaseEvent(eventId),
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
            List<Long> ids = caseFinder.findCases(exceptionRecord, configItem);
            CreateCaseResult result;

            if (ids.isEmpty()) {
                result = createNewCase(exceptionRecord, configItem, ignoreWarnings, idamToken, userId);
            } else if (ids.size() == 1) {
                result = new CreateCaseResult(ids.get(0));
            } else {
                String msg = String.format(
                        "Multiple cases (%s) found for the given bulk scan case reference: %s",
                        ids.stream().map(String::valueOf).collect(joining(", ")),
                        exceptionRecord.id
                );
                log.error(msg);
                throw new MultipleCasesFoundException(msg);
            }

            return handleCaseCreationResult(configItem, exceptionRecordData, result);
        }
    }

    private ProcessResult handleCaseCreationResult(
            ServiceConfigItem configItem,
            CaseDetails exceptionRecordData,
            CreateCaseResult result
    ) {
        if (result.caseId == null) {
            return new ProcessResult(result.warnings, result.errors);
        } else {
            return tryPublishPaymentMessageAndFinalise(
                configItem.getService(),
                    exceptionRecordData,
                Long.toString(result.caseId)
            );
        }
    }

    private CreateCaseResult createNewCase(
            ExceptionRecord exceptionRecord,
            ServiceConfigItem configItem,
            boolean ignoreWarnings,
            String idamToken,
            String userId
    ) {
        CreateCaseResult result;
        result = ccdNewCaseCreator.createNewCase(
                exceptionRecord,
                configItem,
                ignoreWarnings,
                idamToken,
                userId
        );
        if (result.caseId != null) {
            try {
                callbackResultRepositoryProxy.storeCallbackResult(createCaseRequest(
                    exceptionRecord.id,
                    Long.toString(result.caseId)
                ));
            } catch (Exception ex) {
                log.error(
                    "Failed to store callback case creation data to db, exception record Id {}, case Id {}",
                    exceptionRecord.id,
                    result.caseId
                );
            }
        }
        return result;
    }

    private ProcessResult tryPublishPaymentMessageAndFinalise(
        String serviceName,
        CaseDetails exceptionRecordData,
        String caseId
    ) {
        try {
            paymentsProcessor.updatePayments(
                PaymentsHelper.create(
                    exceptionRecordData
                ),
                Long.toString(exceptionRecordData.getId()),
                exceptionRecordData.getJurisdiction(),
                caseId
            );

            return new ProcessResult(
                exceptionRecordFinalizer.finalizeExceptionRecord(
                    exceptionRecordData.getData(),
                    caseId,
                    CcdCallbackType.CASE_CREATION
                )
            );
        } catch (PaymentsPublishingException exception) {
            log.error(
                "Failed to send update to payment processor for {} exception record {}",
                serviceName,
                exceptionRecordData.getId(),
                exception
            );

            return new ProcessResult(
                emptyList(),
                singletonList("Payment references cannot be processed. Please try again later")
            );
        }
    }
}
