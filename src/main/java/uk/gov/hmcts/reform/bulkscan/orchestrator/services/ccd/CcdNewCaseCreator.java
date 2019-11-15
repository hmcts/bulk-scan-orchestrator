package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGenerator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.InvalidCaseDataException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.TransformationClient;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.request.ExceptionRecord;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.response.ResponseCaseDetails;
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

import java.util.Map;

import static java.util.Collections.emptyList;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Service
public class CcdNewCaseCreator {
    private static final Logger log = LoggerFactory.getLogger(CcdNewCaseCreator.class);

    private static final String EXCEPTION_RECORD_REFERENCE = "bulkScanCaseReference";

    private final TransformationClient transformationClient;
    private final AuthTokenGenerator s2sTokenGenerator;
    private final PaymentsProcessor paymentsProcessor;
    private final CoreCaseDataApi coreCaseDataApi;
    private final ExceptionRecordFinalizer exceptionRecordFinalizer;

    public CcdNewCaseCreator(
        TransformationClient transformationClient,
        AuthTokenGenerator s2sTokenGenerator,
        PaymentsProcessor paymentsProcessor,
        CoreCaseDataApi coreCaseDataApi,
        ExceptionRecordFinalizer exceptionRecordFinalizer
    ) {
        this.transformationClient = transformationClient;
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
                exceptionRecordFinalizer.finalizeExceptionRecord(exceptionRecordData.getData(), newCaseId)
            );
        } catch (InvalidCaseDataException exception) {
            if (BAD_REQUEST.equals(exception.getStatus())) {
                throw new CallbackException("Failed to transform exception record", exception);
            } else {
                return new ProcessResult(exception.getResponse().warnings, exception.getResponse().errors);
            }
        } catch (PaymentsPublishingException exception) {
            log.error(
                "Failed to send update to payment processor for {} exception record {}",
                configItem.getService(),
                exceptionRecord.id,
                exception
            );

            throw new CallbackException("Payment references cannot be processed. Please try again later", exception);
        } catch (Exception exception) {
            // log happens individually to cover transformation/ccd cases
            throw new CallbackException("Failed to create new case", exception);
        }
    }

    @SuppressWarnings("squid:S2139") // exception handle + logging
    private long createNewCaseInCcd(
        String idamToken,
        String s2sToken,
        String userId,
        ExceptionRecord exceptionRecord,
        ResponseCaseDetails caseCreationDetails
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
