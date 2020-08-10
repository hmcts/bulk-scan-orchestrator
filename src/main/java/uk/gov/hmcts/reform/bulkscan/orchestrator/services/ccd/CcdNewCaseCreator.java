package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException.BadRequest;
import org.springframework.web.client.HttpClientErrorException.UnprocessableEntity;
import org.springframework.web.client.RestClientException;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGenerator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.ServiceResponseParser;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.response.ClientServiceErrorResponse;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.TransformationClient;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.response.CaseCreationDetails;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.response.SuccessfulTransformationResponse;
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.ServiceConfigItem;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.internal.ExceptionRecord;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.callback.CallbackException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.callback.CreateCaseResult;
import uk.gov.hmcts.reform.ccd.client.model.CaseDataContent;
import uk.gov.hmcts.reform.ccd.client.model.Event;

import java.util.HashMap;
import java.util.Map;
import javax.validation.ConstraintViolationException;

import static java.lang.String.format;
import static java.util.Collections.emptyList;

@Service
public class CcdNewCaseCreator {
    private static final Logger log = LoggerFactory.getLogger(CcdNewCaseCreator.class);

    public static final String EXCEPTION_RECORD_REFERENCE = "bulkScanCaseReference";

    private final TransformationClient transformationClient;
    private final ServiceResponseParser serviceResponseParser;
    private final AuthTokenGenerator s2sTokenGenerator;
    private final CcdApi ccdApi;

    public CcdNewCaseCreator(
        TransformationClient transformationClient,
        ServiceResponseParser serviceResponseParser,
        AuthTokenGenerator s2sTokenGenerator,
        CcdApi ccdApi
    ) {
        this.transformationClient = transformationClient;
        this.serviceResponseParser = serviceResponseParser;
        this.s2sTokenGenerator = s2sTokenGenerator;
        this.ccdApi = ccdApi;
    }

    @SuppressWarnings("squid:S2139") // squid for exception handle + logging
    public CreateCaseResult createNewCase(
        ExceptionRecord exceptionRecord,
        ServiceConfigItem configItem,
        boolean ignoreWarnings,
        String idamToken,
        String userId
    ) {
        log.info(
            "Started creating new case for service {} from exception record {}",
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
                log.info(
                    "Returned warnings after transforming exception record for service {} from exception record {}",
                    configItem.getService(),
                    exceptionRecord.id
                );
                return new CreateCaseResult(transformationResponse.warnings, emptyList());
            }

            log.info(
                "Successfully transformed exception record for service {} from exception record {}",
                configItem.getService(),
                exceptionRecord.id
            );

            long newCaseId = createNewCaseInCcd(
                new CcdRequestCredentials(idamToken, s2sToken, userId),
                exceptionRecord,
                transformationResponse.caseCreationDetails
            );

            log.info(
                "Successfully created new case for service {} with case ID {} from exception record {}",
                configItem.getService(),
                newCaseId,
                exceptionRecord.id
            );

            return new CreateCaseResult(newCaseId);
        } catch (BadRequest exception) {
            throw new CallbackException(
                format("Failed to transform exception record with Id %s", exceptionRecord.id),
                exception
            );
        } catch (UnprocessableEntity exception) {
            ClientServiceErrorResponse errorResponse = serviceResponseParser.parseResponseBody(exception);
            return new CreateCaseResult(errorResponse.warnings, errorResponse.errors);
        // exceptions received from transformation client
        } catch (ConstraintViolationException exception) {
            String message = format(
                "Invalid response received from transformation endpoint. "
                    + "Service: %s, exception record: %s, violations: %s",
                configItem.getService(),
                exceptionRecord.id,
                exception.getMessage()
            );

            throw new CallbackException(message, exception);
        } catch (RestClientException exception) {
            String message = format(
                "Failed to receive transformed exception record from service %s for exception record %s",
                configItem.getService(),
                exceptionRecord.id
            );

            log.error(message, exception);

            throw new CallbackException(message, exception);
        // rest of exceptions received from ccd and logged separately
        } catch (Exception exception) {
            var baseMessage = String.format(
                "Failed to create new case for %s jurisdiction from exception record %s",
                exceptionRecord.poBoxJurisdiction,
                exceptionRecord.id
            );
            var finalMessage = exception instanceof FeignException
                ? baseMessage + ". Service response: " + ((FeignException) exception).contentUTF8()
                : baseMessage;

            log.error(finalMessage, exception);

            throw new CallbackException(
                format(
                    "Failed to create new case for exception record with Id %s. Service: %s",
                    exceptionRecord.id,
                    configItem.getService()
                ),
                exception
            );
        }
    }

    private long createNewCaseInCcd(
        CcdRequestCredentials ccdRequestCredentials,
        ExceptionRecord exceptionRecord,
        CaseCreationDetails caseCreationDetails
    ) {
        var loggingContext = String.format(
            "Exception ID: %s, jurisdiction: %s, form type: %s",
            exceptionRecord.id,
            exceptionRecord.poBoxJurisdiction,
            exceptionRecord.formType
        );

        return ccdApi.createNewCaseFromCallback(
            ccdRequestCredentials.idamToken,
            ccdRequestCredentials.s2sToken,
            ccdRequestCredentials.userId,
            exceptionRecord.poBoxJurisdiction,
            caseCreationDetails.caseTypeId,
            // when onboarding remind services to not configure about to submit callback for this event
            caseCreationDetails.eventId,
            startEventResponse -> getCaseDataContent(
                caseCreationDetails.caseData,
                exceptionRecord.id,
                startEventResponse.getEventId(),
                startEventResponse.getToken()
            ),
            loggingContext
        );
    }

    private CaseDataContent getCaseDataContent(
        Map<String, Object> caseData,
        String exceptionRecordId,
        String eventId,
        String eventToken
    ) {
        Map<String, Object> data = new HashMap<>(caseData);
        data.put(EXCEPTION_RECORD_REFERENCE, exceptionRecordId);

        return CaseDataContent
            .builder()
            .caseReference(exceptionRecordId)
            .data(data)
            .event(Event
                .builder()
                .id(eventId)
                .summary("Case created")
                .description("Case created from exception record ref " + exceptionRecordId)
                .build()
            )
            .eventToken(eventToken)
            .build();
    }
}
