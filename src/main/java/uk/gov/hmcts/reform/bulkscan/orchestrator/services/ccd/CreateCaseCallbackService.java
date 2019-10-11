package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import com.google.common.collect.ImmutableMap;
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
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.config.ServiceNotConfiguredException;
import uk.gov.hmcts.reform.ccd.client.CoreCaseDataApi;
import uk.gov.hmcts.reform.ccd.client.model.CaseDataContent;
import uk.gov.hmcts.reform.ccd.client.model.Event;
import uk.gov.hmcts.reform.ccd.client.model.StartEventResponse;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
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
     */
    public ProcessResult process(
        CcdCallbackRequest request,
        String idamToken,
        String userId
    ) {
        Validation<String, Void> event = isCreateNewCaseEvent(request.getEventId());
        if (event.isInvalid()) {
            return error(event.getError());
        } else {
            return hasServiceNameInCaseTypeId(request.getCaseDetails())
                .map(serviceName -> {
                    try {
                        ServiceConfigItem serviceCfg = serviceConfigProvider.getConfig(serviceName);
                        if (serviceCfg == null || serviceCfg.getTransformationUrl() == null) {
                            return error("Transformation URL is not configured");
                        } else {
                            return validator
                                .getValidation(request.getCaseDetails())
                                .map(exceptionRecord -> createNewCase(
                                    exceptionRecord,
                                    serviceCfg,
                                    request.getCaseDetails().getId(),
                                    request.isIgnoreWarnings(),
                                    idamToken,
                                    userId
                                ))
                                .getOrElseGet(errors -> new ProcessResult(emptyList(), errors.asJava()));
                        }
                    } catch (ServiceNotConfiguredException exc) {
                        return error(exc.getMessage());
                    }
                })
                .getOrElseGet(err -> error(err));
        }
    }

    private ProcessResult createNewCase(
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
                return new ProcessResult(transformationResponse.warnings, emptyList());
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

            return new ProcessResult(ImmutableMap.of(CASE_REFERENCE, Long.toString(newCaseId)));
        } catch (InvalidCaseDataException exception) {
            if (BAD_REQUEST.equals(exception.getStatus())) {
                throw exception;
            } else {
                return new ProcessResult(
                    exception.getResponse().warnings,
                    exception.getResponse().errors
                );
            }
        } catch (Exception exception) {
            log.error(
                "Failed to create exception for service {} and exception record {}",
                configItem.getService(),
                caseIdAsString,
                exception
            );

            return error("Internal error. " + exception.getMessage());
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
            // when onboarding remind services to not configure about to submit callback for this event
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

    private ProcessResult error(String message) {
        return new ProcessResult(emptyList(), singletonList(message));
    }
}
