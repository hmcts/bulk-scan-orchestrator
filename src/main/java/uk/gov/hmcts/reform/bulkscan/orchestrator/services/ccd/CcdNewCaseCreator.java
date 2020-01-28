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
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.callback.ProcessResult;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.payments.PaymentsPublishingException;
import uk.gov.hmcts.reform.ccd.client.CoreCaseDataApi;
import uk.gov.hmcts.reform.ccd.client.model.CaseDataContent;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.ccd.client.model.Event;
import uk.gov.hmcts.reform.ccd.client.model.StartEventResponse;

import java.util.HashMap;
import java.util.Map;

import static java.lang.String.format;
import static java.util.Collections.emptyList;

@Service
public class CcdNewCaseCreator {
    private static final Logger log = LoggerFactory.getLogger(CcdNewCaseCreator.class);

    private static final String EXCEPTION_RECORD_REFERENCE = "bulkScanCaseReference";

    private final TransformationClient transformationClient;
    private final ServiceResponseParser serviceResponseParser;
    private final AuthTokenGenerator s2sTokenGenerator;
    private final PaymentsProcessor paymentsProcessor;
    private final CoreCaseDataApi coreCaseDataApi;
    private final ExceptionRecordFinalizer exceptionRecordFinalizer;

    public CcdNewCaseCreator(
        TransformationClient transformationClient,
        ServiceResponseParser serviceResponseParser, AuthTokenGenerator s2sTokenGenerator,
        PaymentsProcessor paymentsProcessor,
        CoreCaseDataApi coreCaseDataApi,
        ExceptionRecordFinalizer exceptionRecordFinalizer
    ) {
        this.transformationClient = transformationClient;
        this.serviceResponseParser = serviceResponseParser;
        this.s2sTokenGenerator = s2sTokenGenerator;
        this.paymentsProcessor = paymentsProcessor;
        this.coreCaseDataApi = coreCaseDataApi;
        this.exceptionRecordFinalizer = exceptionRecordFinalizer;
    }

    @SuppressWarnings({"squid:S2139", "unchecked"}) // squid for exception handle + logging
    public ProcessResult createNewCase(
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
                log.info(
                    "Returned warnings after transforming exception record for {} from exception record {}",
                    configItem.getService(),
                    exceptionRecord.id
                );
                return new ProcessResult(transformationResponse.warnings, emptyList());
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

            paymentsProcessor.updatePayments(exceptionRecordData, newCaseId);

            return new ProcessResult(
                exceptionRecordFinalizer.finalizeExceptionRecord(
                    exceptionRecordData.getData(),
                    newCaseId,
                    CcdCallbackType.CASE_CREATION
                )
            );
        } catch (BadRequest exception) {
            throw new CallbackException(
                format("Failed to transform exception record with Id %s", exceptionRecord.id),
                exception
            );
        } catch (UnprocessableEntity exception) {
            ClientServiceErrorResponse errorResponse = serviceResponseParser.parseResponseBody(exception);
            return new ProcessResult(errorResponse.warnings, errorResponse.errors);
        } catch (PaymentsPublishingException exception) {
            log.error(
                "Failed to send update to payment processor for {} exception record {}",
                configItem.getService(),
                exceptionRecord.id,
                exception
            );

            throw new CallbackException("Payment references cannot be processed. Please try again later", exception);
        // I/O related exception received from transformation client
        } catch (RestClientException exception) {
            String message = format(
                "Failed to receive transformed exception record from %s client for exception record %s",
                configItem.getService(),
                exceptionRecord.id
            );

            log.error(message, exception);

            throw new CallbackException(message, exception);
        } catch (Exception exception) {
            // log happens individually to cover transformation/ccd cases
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
