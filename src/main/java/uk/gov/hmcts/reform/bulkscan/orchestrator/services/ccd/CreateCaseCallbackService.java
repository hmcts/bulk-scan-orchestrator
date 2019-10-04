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
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGenerator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.InvalidCaseDataException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.TransformationClient;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.request.ExceptionRecord;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.response.CaseCreationDetails;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.response.SuccessfulTransformationResponse;
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.ServiceConfigItem;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.in.CcdCallbackRequest;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.callback.CreateCaseValidator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.callback.ProcessResult;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.config.ServiceConfigProvider;
import uk.gov.hmcts.reform.ccd.client.CoreCaseDataApi;
import uk.gov.hmcts.reform.ccd.client.model.CaseDataContent;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.ccd.client.model.Event;
import uk.gov.hmcts.reform.ccd.client.model.StartEventResponse;

import java.util.List;
import java.util.function.Function;

import static java.util.Collections.emptyList;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CallbackValidations.hasServiceNameInCaseTypeId;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.EventIdValidator.isCreateNewCaseEvent;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.ExceptionRecordFields.CASE_REFERENCE;


@Service
public class CreateCaseCallbackService {

    private static final Logger log = LoggerFactory.getLogger(CreateCaseCallbackService.class);

    private final CreateCaseValidator validator;
    private final ServiceConfigProvider serviceConfigProvider;
    private final TransformationClient transformationClient;
    private final AuthTokenGenerator s2sTokenGenerator;
    private final CoreCaseDataApi ccdApi;

    public CreateCaseCallbackService(
        CreateCaseValidator validator,
        ServiceConfigProvider serviceConfigProvider,
        TransformationClient transformationClient,
        AuthTokenGenerator s2sTokenGenerator,
        CoreCaseDataApi ccdApi
    ) {
        this.validator = validator;
        this.serviceConfigProvider = serviceConfigProvider;
        this.transformationClient = transformationClient;
        this.s2sTokenGenerator = s2sTokenGenerator;
        this.ccdApi = ccdApi;
    }

    /**
     * Create case record from exception case record.
     *
     * @return Either list of errors or map of changes - new case reference
     */
    public Either<List<String>, ProcessResult> process(
        CcdCallbackRequest request,
        String idamToken,
        String userId
    ) {
        return assertAllowToAccess(request.getCaseDetails(), request.getEventId())
            .flatMap(theVoid -> validator
                .getValidation(request.getCaseDetails())
                .combine(getServiceConfig(request.getCaseDetails()).mapError(Array::of))
                .ap((exceptionRecord, configItem) -> createNewCase(
                    exceptionRecord,
                    configItem,
                    request.getCaseDetails().getId(),
                    request.isIgnoreWarnings(),
                    idamToken,
                    userId
                ))
                .mapError(errors -> errors.flatMap(Function.identity()))
                .flatMap(Function.identity())
                .toEither()
                .mapLeft(Seq::asJava)
            );
    }

    private Either<List<String>, Void> assertAllowToAccess(CaseDetails caseDetails, String eventId) {
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

    private Validation<Seq<String>, ProcessResult> createNewCase(
        ExceptionRecord exceptionRecord,
        ServiceConfigItem configItem,
        long caseId,
        boolean ignoreWarnings,
        String idamToken,
        String userId
    ) {
        String caseIdAsString = Long.toString(caseId);

        log.info("Start creating new case for {} from exception record {}", configItem.getService(), caseIdAsString);

        try {
            String s2sToken = s2sTokenGenerator.generate();

            SuccessfulTransformationResponse transformationResponse = transformationClient.transformExceptionRecord(
                configItem.getTransformationUrl(),
                exceptionRecord,
                s2sToken
            );

            if (!ignoreWarnings && !transformationResponse.warnings.isEmpty()) {
                // do not log warnings
                return Validation.valid(new ProcessResult(
                    transformationResponse.warnings,
                    emptyList()
                ));
            }

            log.info(
                "Successfully transformed exception record for {} from exception record {}",
                configItem.getService(),
                caseIdAsString
            );

            long newCaseId = createNewCaseInCcd(
                idamToken,
                s2sToken,
                userId,
                exceptionRecord.poBoxJurisdiction,
                transformationResponse.caseCreationDetails,
                caseIdAsString
            );

            log.info(
                "Successfully created new case for {} with case ID {} from exception record {}",
                configItem.getService(),
                newCaseId,
                caseIdAsString
            );

            return Validation.valid(new ProcessResult(
                ImmutableMap.of(CASE_REFERENCE, Long.toString(newCaseId))
            ));
        } catch (InvalidCaseDataException exception) {
            if (BAD_REQUEST.equals(exception.getStatus())) {
                throw exception;
            } else {
                return Validation.valid(new ProcessResult(
                    exception.getResponse().warnings,
                    exception.getResponse().errors
                ));
            }
        } catch (Exception exception) {
            log.error(
                "Failed to create exception for service {} and exception record {}",
                configItem.getService(),
                caseIdAsString,
                exception
            );

            return Validation.invalid(Array.of("Internal error. " + exception.getClass().getName()
                + " "  + exception.getMessage()));
        }
    }

    private long createNewCaseInCcd(
        String idamToken,
        String s2sToken,
        String userId,
        String jurisdiction,
        CaseCreationDetails caseCreationDetails,
        String originalCaseId
    ) {
        StartEventResponse eventResponse = ccdApi.startForCaseworker(
            idamToken,
            s2sToken,
            userId,
            jurisdiction,
            caseCreationDetails.caseTypeId,
            caseCreationDetails.eventId
        );

        return ccdApi.submitForCaseworker(
            idamToken,
            s2sToken,
            userId,
            jurisdiction,
            caseCreationDetails.caseTypeId,
            true,
            CaseDataContent
                .builder()
                .caseReference(originalCaseId)
                .data(caseCreationDetails.caseData)
                .event(Event
                    .builder()
                    .id(eventResponse.getEventId())
                    .summary("Case created")
                    .description("Case created from exception record ref " + originalCaseId)
                    .build()
                )
                .eventToken(eventResponse.getToken())
                .build()
        ).getId();
    }
}
