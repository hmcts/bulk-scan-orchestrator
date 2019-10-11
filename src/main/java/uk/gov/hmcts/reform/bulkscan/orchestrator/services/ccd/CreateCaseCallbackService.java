package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import com.google.common.collect.ImmutableMap;
import io.vavr.control.Validation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGenerator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.InvalidCaseDataException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.TransformationClient;
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
            return new ProcessResult(emptyList(), singletonList(event.getError()));
        } else {
            return hasServiceNameInCaseTypeId(request.getCaseDetails())
                .map(serviceName -> {
                    try {
                        ServiceConfigItem serviceCfg = serviceConfigProvider.getConfig(serviceName);
                        if (serviceCfg == null || serviceCfg.getTransformationUrl() == null) {
                            return new ProcessResult(emptyList(), singletonList("Transformation URL is not configured"));
                        } else {
                            return validator
                                .getValidation(request.getCaseDetails())
                                .map(exceptionRecord -> {
                                    SuccessfulTransformationResponse transformationResp =
                                        transformationClient.transformExceptionRecord(
                                            serviceCfg.getTransformationUrl(),
                                            exceptionRecord,
                                            s2sTokenGenerator.generate()
                                        );
                                    long newCaseId = createNewCaseInCcd(
                                        idamToken,
                                        userId,
                                        exceptionRecord.poBoxJurisdiction,
                                        transformationResp.caseCreationDetails,
                                        request.getCaseDetails().getId().toString()
                                    );
                                    return new ProcessResult(ImmutableMap.of(CASE_REFERENCE, Long.toString(newCaseId)));
                                })
                                .getOrElseGet(errors -> new ProcessResult(emptyList(), errors.asJava()));
                        }
                    } catch (ServiceNotConfiguredException exc) {
                        return new ProcessResult(emptyList(), singletonList(exc.getMessage()));
                    } catch (InvalidCaseDataException exc) {
                        return new ProcessResult(exc.getResponse().warnings, exc.getResponse().errors);
                    } catch (Exception exc) {
                        log.error("Error handling event", exc);
                        return new ProcessResult(emptyList(), singletonList("Internal error"));
                    }
                })
                .getOrElseGet(err -> new ProcessResult(emptyList(), singletonList(err)));
        }
    }

    private long createNewCaseInCcd(
        String idamToken,
        String userId,
        String jurisdiction,
        CaseCreationDetails caseCreationDetails,
        String originalCaseId
    ) {
        String s2sToken = s2sTokenGenerator.generate();

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
}
