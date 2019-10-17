package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import feign.FeignException;
import io.vavr.collection.Seq;
import io.vavr.control.Try;
import io.vavr.control.Validation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGenerator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.InvalidCaseDataException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.TransformationClient;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.request.ExceptionRecord;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.response.CaseCreationDetails;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.response.SuccessfulTransformationResponse;
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.ServiceConfigItem;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.in.CcdCallbackRequest;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.callback.CallbackException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.callback.CreateCaseValidator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.callback.ProcessResult;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.YesNoFieldValues;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.config.ServiceConfigProvider;
import uk.gov.hmcts.reform.ccd.client.CoreCaseDataApi;
import uk.gov.hmcts.reform.ccd.client.model.CaseDataContent;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.ccd.client.model.Event;
import uk.gov.hmcts.reform.ccd.client.model.StartEventResponse;

import java.util.List;
import java.util.Map;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.joining;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
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
    private final TransformationClient transformationClient;
    private final AuthTokenGenerator s2sTokenGenerator;
    private final PaymentsProcessor paymentsProcessor;
    private final CoreCaseDataApi coreCaseDataApi;
    private final CcdApi ccdApi;

    public CreateCaseCallbackService(
        CreateCaseValidator validator,
        ServiceConfigProvider serviceConfigProvider,
        TransformationClient transformationClient,
        AuthTokenGenerator s2sTokenGenerator,
        PaymentsProcessor paymentsProcessor,
        CoreCaseDataApi coreCaseDataApi,
        CcdApi ccdApi
    ) {
        this.validator = validator;
        this.serviceConfigProvider = serviceConfigProvider;
        this.transformationClient = transformationClient;
        this.s2sTokenGenerator = s2sTokenGenerator;
        this.paymentsProcessor = paymentsProcessor;
        this.coreCaseDataApi = coreCaseDataApi;
        this.ccdApi = ccdApi;
    }

    /**
     * Create case record from exception case record.
     *
     * @return ProcessResult map of changes or list of errors/warnings
     */
    public ProcessResult process(CcdCallbackRequest request, String idamToken, String userId) {
        Validation<String, Void> canAccess = assertAllowToAccess(request.getCaseDetails(), request.getEventId());

        if (canAccess.isInvalid()) {
            log.warn("Validation error: {}", canAccess.getError());

            throw new CallbackException(canAccess.getError());
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
                "Warnings found for {} during callback process: {}",
                serviceConfigItem.getService(),
                result.getWarnings().size()
            );
        }

        if (!result.getErrors().isEmpty()) {
            // no need to error - it's informational log. specific logs will be error'ed already
            log.warn(
                "Errors found for {} during callback process: {}",
                serviceConfigItem.getService(),
                result.getErrors().size()
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
            return createNewCase(
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

    @SuppressWarnings("unchecked")
    private ProcessResult createNewCase(
        ExceptionRecord exceptionRecord,
        ServiceConfigItem configItem,
        boolean ignoreWarnings,
        String idamToken,
        String userId,
        CaseDetails exceptionRecordData
    ) {
        log.info(
            "Start creating new case for {} from exception record {}",
            configItem.getService(),
            exceptionRecord.id
        );

        try {
            String s2sToken = s2sTokenGenerator.generate();

            SuccessfulTransformationResponse transformationResponse = transformationClient.transformExceptionRecord(
                configItem.getTransformationUrl(),
                exceptionRecord,
                s2sToken
            );

            if (!ignoreWarnings && !transformationResponse.warnings.isEmpty()) {
                // do not log warnings
                return new ProcessResult(transformationResponse.warnings, emptyList());
            }

            log.info(
                "Successfully transformed exception record for {} from exception record {}",
                configItem.getService(),
                exceptionRecord.id
            );

            checkBulkScanReferenceIsSet(
                (Map<String, ?>) transformationResponse.caseCreationDetails.caseData,
                exceptionRecord.id
            );

            long newCaseId = createNewCaseInCcd(
                idamToken,
                s2sToken,
                userId,
                exceptionRecord,
                transformationResponse.caseCreationDetails
            );

            log.info(
                "Successfully created new case for {} with case ID {} from exception record {}",
                configItem.getService(),
                newCaseId,
                exceptionRecord.id
            );

            paymentsProcessor.updatePayments(exceptionRecordData, newCaseId);

            return new ProcessResult(
                ImmutableMap.<String, Object>builder()
                    .put(CASE_REFERENCE, Long.toString(newCaseId))
                    .put(DISPLAY_WARNINGS, YesNoFieldValues.NO)
                    .put(OCR_DATA_VALIDATION_WARNINGS, emptyList())
                    .build()
            );
        } catch (InvalidCaseDataException exception) {
            if (BAD_REQUEST.equals(exception.getStatus())) {
                throw exception;
            } else {
                return new ProcessResult(exception.getResponse().warnings, exception.getResponse().errors);
            }
        } catch (Exception exception) {
            // log happens individually to cover transformation/ccd cases
            throw new CallbackException("Failed to create new case", exception);
        }
    }

    private long createNewCaseInCcd(
        String idamToken,
        String s2sToken,
        String userId,
        ExceptionRecord exceptionRecord,
        CaseCreationDetails caseCreationDetails
    ) {
        try {
            StartEventResponse eventResponse = coreCaseDataApi.startForCaseworker(
                idamToken,
                s2sToken,
                userId,
                exceptionRecord.poBoxJurisdiction,
                caseCreationDetails.caseTypeId,
                // when onboarding remind services to not configure about to submit callback for this event
                caseCreationDetails.eventId
            );

            return coreCaseDataApi.submitForCaseworker(
                idamToken,
                s2sToken,
                userId,
                exceptionRecord.poBoxJurisdiction,
                caseCreationDetails.caseTypeId,
                true,
                CaseDataContent
                    .builder()
                    .caseReference(exceptionRecord.id)
                    .data(caseCreationDetails.caseData)
                    .event(Event
                        .builder()
                        .id(eventResponse.getEventId())
                        .summary("Case created")
                        .description("Case created from exception record ref " + exceptionRecord.id)
                        .build()
                    )
                    .eventToken(eventResponse.getToken())
                    .build()
            ).getId();
        } catch (FeignException exception) {
            log.error(
                "Failed to create new case for {} jurisdiction from exception record {}. Service response: {}",
                exceptionRecord.poBoxJurisdiction,
                exceptionRecord.id,
                exception.contentUTF8(),
                exception
            );

            throw exception;
        } catch (Exception exception) {
            log.error(
                "Failed to create new case for {} jurisdiction from exception record {}",
                exceptionRecord.poBoxJurisdiction,
                exceptionRecord.id,
                exception
            );

            throw exception;
        }
    }

    private void checkBulkScanReferenceIsSet(Map<String, ?> caseData, String exceptionRecordId) {
        if (!caseData.containsKey(EXCEPTION_RECORD_REFERENCE)) {
            log.error(
                "Transformation did not set '{}' with exception record id {}",
                EXCEPTION_RECORD_REFERENCE,
                exceptionRecordId
            );
        } else if (!caseData.get(EXCEPTION_RECORD_REFERENCE).equals(exceptionRecordId)) {
            log.error(
                "Transformation did not set exception record reference correctly. Actual: {}, expected: {}",
                caseData.get(EXCEPTION_RECORD_REFERENCE),
                exceptionRecordId
            );
        }
    }
}
