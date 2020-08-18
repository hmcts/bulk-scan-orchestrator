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
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.ExceptionRecordTransformer;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.response.CaseCreationDetails;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.response.SuccessfulTransformationResponse;
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.ServiceConfigItem;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.CaseAction;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.internal.ExceptionRecord;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.callback.CallbackException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.callback.CreateCaseResult;
import uk.gov.hmcts.reform.ccd.client.model.CaseDataContent;
import uk.gov.hmcts.reform.ccd.client.model.Event;
import uk.gov.hmcts.reform.ccd.client.model.StartEventResponse;

import java.util.HashMap;
import java.util.Map;
import javax.validation.ConstraintViolationException;

import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.ServiceCaseFields.BULK_SCAN_CASE_REFERENCE;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.ServiceCaseFields.BULK_SCAN_ENVELOPES;

@Service
public class CcdNewCaseCreator {
    private static final Logger log = LoggerFactory.getLogger(CcdNewCaseCreator.class);

    private final ExceptionRecordTransformer exceptionRecordTransformer;
    private final ServiceResponseParser serviceResponseParser;
    private final AuthTokenGenerator s2sTokenGenerator;
    private final CcdApi ccdApi;
    private final EnvelopeReferenceCollectionHelper envelopeReferenceCollectionHelper;

    public CcdNewCaseCreator(
        ExceptionRecordTransformer exceptionRecordTransformer,
        ServiceResponseParser serviceResponseParser,
        AuthTokenGenerator s2sTokenGenerator,
        CcdApi ccdApi,
        EnvelopeReferenceCollectionHelper envelopeReferenceCollectionHelper
    ) {
        this.exceptionRecordTransformer = exceptionRecordTransformer;
        this.serviceResponseParser = serviceResponseParser;
        this.s2sTokenGenerator = s2sTokenGenerator;
        this.ccdApi = ccdApi;
        this.envelopeReferenceCollectionHelper = envelopeReferenceCollectionHelper;
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
            SuccessfulTransformationResponse transformationResponse =
                exceptionRecordTransformer.transformExceptionRecord(configItem.getTransformationUrl(), exceptionRecord);

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
                new CcdRequestCredentials(idamToken, s2sTokenGenerator.generate(), userId),
                exceptionRecord,
                transformationResponse.caseCreationDetails,
                configItem.getService()
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
        CaseCreationDetails caseCreationDetails,
        String service
    ) {
        var loggingContext = String.format(
            "Exception ID: %s, jurisdiction: %s, form type: %s",
            exceptionRecord.id,
            exceptionRecord.poBoxJurisdiction,
            exceptionRecord.formType
        );

        return ccdApi.createCase(
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
                exceptionRecord.envelopeId,
                startEventResponse,
                service
            ),
            loggingContext
        );
    }

    private CaseDataContent getCaseDataContent(
        Map<String, Object> caseData,
        String exceptionRecordId,
        String envelopeId,
        StartEventResponse startEventResponse,
        String service
    ) {
        Map<String, Object> completeCaseData =
            setBulkScanSpecificFieldsInCaseData(caseData, service, exceptionRecordId, envelopeId);

        return CaseDataContent
            .builder()
            .caseReference(exceptionRecordId)
            .data(completeCaseData)
            .event(Event
                .builder()
                .id(startEventResponse.getEventId())
                .summary("Case created")
                .description("Case created from exception record ref " + exceptionRecordId)
                .build()
            )
            .eventToken(startEventResponse.getToken())
            .build();
    }

    private Map<String, Object> setBulkScanSpecificFieldsInCaseData(
        Map<String, Object> caseData,
        String service,
        String exceptionRecordId,
        String envelopeId
    ) {
        Map<String, Object> updatedCaseData = new HashMap<>(caseData);
        updatedCaseData.put(BULK_SCAN_CASE_REFERENCE, exceptionRecordId);

        if (envelopeReferenceCollectionHelper.serviceSupportsEnvelopeReferences(service)) {
            updatedCaseData.put(
                BULK_SCAN_ENVELOPES,
                envelopeReferenceCollectionHelper.singleEnvelopeReferenceList(envelopeId, CaseAction.CREATE)
            );
        }

        return updatedCaseData;
    }
}
