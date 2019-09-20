package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import io.vavr.collection.Array;
import io.vavr.collection.Seq;
import io.vavr.control.Either;
import io.vavr.control.Try;
import io.vavr.control.Validation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.TransformationClient;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.request.ExceptionRecord;
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.ServiceConfigItem;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.callback.CreateCaseValidator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.config.ServiceConfigProvider;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CallbackValidations.hasServiceNameInCaseTypeId;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.EventIdValidator.isCreateCaseEvent;

@Service
public class CreateCaseCallbackService {

    private static final Logger log = LoggerFactory.getLogger(CreateCaseCallbackService.class);

    private final CreateCaseValidator validator;
    private final ServiceConfigProvider serviceConfigProvider;
    private final TransformationClient transformationClient;

    public CreateCaseCallbackService(
        CreateCaseValidator validator,
        ServiceConfigProvider serviceConfigProvider,
        TransformationClient transformationClient
    ) {
        this.validator = validator;
        this.serviceConfigProvider = serviceConfigProvider;
        this.transformationClient = transformationClient;
    }

    /**
     * Create case record from exception case record.
     *
     * @return Either list of errors or map of changes - new case reference
     */
    public Either<List<String>, Map<String, Object>> process(CaseDetails caseDetails, String eventId) {
        return assertAllowToAccess(caseDetails, eventId)
            .flatMap(theVoid -> validator
                .getValidation(caseDetails)
                .combine(getServiceConfig(caseDetails).mapError(Array::of))
                .ap((exceptionRecord, configItem) -> createNewCase(
                    exceptionRecord,
                    configItem,
                    caseDetails.getId()
                ))
                .mapError(errors -> errors.flatMap(Function.identity()))
                .flatMap(Function.identity())
                .toEither()
                .mapLeft(Seq::asJava)
            );
    }

    private Either<List<String>, Void> assertAllowToAccess(CaseDetails caseDetails, String eventId) {
        return validator.mandatoryPrerequisites(
            () -> isCreateCaseEvent(eventId),
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

    private Validation<Seq<String>, Map<String, Object>> createNewCase(
        ExceptionRecord exceptionRecord,
        ServiceConfigItem configItem,
        long caseId
    ) {
        try {
            log.info(
                "Start creating exception record for service {} and exception record {}",
                configItem.getService(),
                caseId
            );

            transformationClient.transformExceptionRecord(
                configItem.getTransformationUrl(),
                exceptionRecord,
                "s2s token"
            );

            return Validation.valid(ImmutableMap.of("caseReference", UUID.randomUUID()));
        } catch (Exception exception) {
            log.error(
                "Failed to create exception for service {} and exception record {}",
                configItem.getService(),
                caseId,
                exception
            );

            return Validation.invalid(Array.of("Internal error. " + exception.getMessage()));
        }
    }
}
