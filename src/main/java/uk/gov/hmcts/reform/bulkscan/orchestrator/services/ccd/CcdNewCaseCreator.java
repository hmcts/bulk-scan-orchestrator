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
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.request.ExceptionRecord;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.response.ClientServiceErrorResponse;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.TransformationClient;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.response.CaseCreationDetails;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.response.SuccessfulTransformationResponse;
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.ServiceConfigItem;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.callback.CallbackException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.callback.CreateCaseResult;
import uk.gov.hmcts.reform.ccd.client.CoreCaseDataApi;
import uk.gov.hmcts.reform.ccd.client.model.CaseDataContent;
import uk.gov.hmcts.reform.ccd.client.model.Event;
import uk.gov.hmcts.reform.ccd.client.model.StartEventResponse;

import java.util.HashMap;
import java.util.Map;
import javax.validation.ConstraintViolationException;

import static java.lang.String.format;
import static java.util.Collections.emptyList;

@Service
public class CcdNewCaseCreator {
    private static final Logger log = LoggerFactory.getLogger(CcdNewCaseCreator.class);

    private static final String EXCEPTION_RECORD_REFERENCE = "bulkScanCaseReference";

    private final TransformationClient transformationClient;
    private final ServiceResponseParser serviceResponseParser;
    private final AuthTokenGenerator s2sTokenGenerator;
    private final CoreCaseDataApi coreCaseDataApi;

    public CcdNewCaseCreator(
        TransformationClient transformationClient,
        ServiceResponseParser serviceResponseParser,
        AuthTokenGenerator s2sTokenGenerator,
        CoreCaseDataApi coreCaseDataApi
    ) {
        this.transformationClient = transformationClient;
        this.serviceResponseParser = serviceResponseParser;
        this.s2sTokenGenerator = s2sTokenGenerator;
        this.coreCaseDataApi = coreCaseDataApi;
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
                log.info(
                    "Returned warnings after transforming exception record for {} from exception record {}",
                    configItem.getService(),
                    exceptionRecord.id
                );
                return new CreateCaseResult(transformationResponse.warnings, emptyList());
            }

            log.info(
                "Successfully transformed exception record for {} from exception record {}",
                configItem.getService(),
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

            log.error(message, exception);

            throw new CallbackException(message, exception);
        } catch (RestClientException exception) {
            String message = format(
                "Failed to receive transformed exception record from %s client for exception record %s",
                configItem.getService(),
                exceptionRecord.id
            );

            log.error(message, exception);

            throw new CallbackException(message, exception);
        // rest of exceptions received from ccd and logged separately
        } catch (Exception exception) {
            throw new CallbackException(
                format("Failed to create new case for exception record with Id %s", exceptionRecord.id),
                exception
            );
        }
    }

    @SuppressWarnings("squid:S2139") // exception handle + logging
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

            log.info(
                "Started event for creating case from exception record. "
                    + "Event ID: {}. Exception record ID: {}. Jurisdiction: {}. Case type: {}",
                caseCreationDetails.eventId,
                exceptionRecord.id,
                exceptionRecord.poBoxJurisdiction,
                caseCreationDetails.caseTypeId
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
                    .data(caseDataWithExceptionRecordId(
                        caseCreationDetails.caseData, exceptionRecord.id
                    )) // set bulk scan case reference
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

    @SuppressWarnings("unchecked")
    private Map<String, Object> caseDataWithExceptionRecordId(Object caseData, String exceptionRecordId) {
        Map<String, Object> data = new HashMap((Map<String, Object>) caseData);
        data.put(EXCEPTION_RECORD_REFERENCE, exceptionRecordId);
        return data;
    }
}
