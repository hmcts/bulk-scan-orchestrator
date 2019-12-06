package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException.BadRequest;
import org.springframework.web.client.HttpClientErrorException.UnprocessableEntity;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGenerator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.ServiceResponseParser;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.caseupdate.CaseUpdateClient;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.caseupdate.model.response.CaseUpdateDetails;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.caseupdate.model.response.SuccessfulUpdateResponse;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.request.ExceptionRecord;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.response.ClientServiceErrorResponse;
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.ServiceConfigItem;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.callback.CallbackException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.callback.ProcessResult;
import uk.gov.hmcts.reform.ccd.client.CoreCaseDataApi;
import uk.gov.hmcts.reform.ccd.client.model.CaseDataContent;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.ccd.client.model.Event;
import uk.gov.hmcts.reform.ccd.client.model.StartEventResponse;

import static java.lang.String.format;
import static java.util.Collections.emptyList;

@Service
public class CcdCaseUpdater {
    private static final Logger log = LoggerFactory.getLogger(CcdCaseUpdater.class);

    private final CaseUpdateClient caseUpdateClient;
    private final ServiceResponseParser serviceResponseParser;
    private final AuthTokenGenerator s2sTokenGenerator;
    private final CoreCaseDataApi coreCaseDataApi;
    private final ExceptionRecordFinalizer exceptionRecordFinalizer;

    public CcdCaseUpdater(
        CaseUpdateClient caseUpdateClient,
        ServiceResponseParser serviceResponseParser,
        AuthTokenGenerator s2sTokenGenerator,
        CoreCaseDataApi coreCaseDataApi,
        ExceptionRecordFinalizer exceptionRecordFinalizer
    ) {
        this.caseUpdateClient = caseUpdateClient;
        this.serviceResponseParser = serviceResponseParser;
        this.s2sTokenGenerator = s2sTokenGenerator;
        this.coreCaseDataApi = coreCaseDataApi;
        this.exceptionRecordFinalizer = exceptionRecordFinalizer;
    }

    public ProcessResult updateCase(
        ExceptionRecord exceptionRecord,
        ServiceConfigItem configItem,
        boolean ignoreWarnings,
        String idamToken,
        String userId,
        String existingCaseId,
        String eventId
    ) {
        log.info(
            "Start updating case for service {} with case Id {} from exception record {}",
            configItem.getService(),
            existingCaseId,
            exceptionRecord.id
        );

        try {
            String s2sToken = s2sTokenGenerator.generate();

            StartEventResponse eventResponse = coreCaseDataApi.startEventForCaseWorker(
                idamToken,
                s2sToken,
                userId,
                exceptionRecord.poBoxJurisdiction,
                exceptionRecord.caseTypeId,
                existingCaseId,
                eventId
            );

            SuccessfulUpdateResponse updateResponse = caseUpdateClient.updateCase(
                configItem.getUpdateUrl(),
                eventResponse.getCaseDetails(),
                exceptionRecord,
                s2sToken
            );

            log.info(
                "Successfully called case update endpoint of service {} to update case with case Id {} "
                    + "based on exception record ref {}",
                configItem.getService(),
                existingCaseId,
                exceptionRecord.id
            );

            if (!ignoreWarnings && !updateResponse.warnings.isEmpty()) {
                log.info(
                    "Returned warnings after calling case update endpoint of service {} to update case with case Id {} "
                        + "based on exception record ref {}",
                    configItem.getService(),
                    existingCaseId,
                    exceptionRecord.id
                );
                return new ProcessResult(updateResponse.warnings, emptyList());
            } else {
                updateCaseInCcd(
                    configItem.getService(),
                    ignoreWarnings,
                    idamToken,
                    s2sToken,
                    userId,
                    exceptionRecord,
                    updateResponse.caseDetails,
                    eventResponse
                );

                return new ProcessResult(
                    exceptionRecordFinalizer.finalizeExceptionRecord(
                        eventResponse.getCaseDetails().getData(),
                        eventResponse.getCaseDetails().getId()
                    )
                );
            }
        } catch (BadRequest exception) {
            throw new CallbackException(
                format(
                    "Failed to call %s service Case Update API to update case with case Id %s "
                        + "based on exception record %s",
                    configItem.getService(),
                    existingCaseId,
                    exceptionRecord.id
                ),
                exception
            );
        } catch (UnprocessableEntity exception) {
            ClientServiceErrorResponse errorResponse = serviceResponseParser.parseResponseBody(exception);
            return new ProcessResult(errorResponse.warnings, errorResponse.errors);
        } catch (Exception exception) {
            throw new CallbackException(
                format(
                    "Failed to update case for %s service with case Id %s based on exception record %s",
                    configItem.getService(),
                    existingCaseId,
                    exceptionRecord.id
                ),
                exception
            );
        }
    }

    private void updateCaseInCcd(
        String service,
        boolean ignoreWarnings,
        String idamToken,
        String s2sToken,
        String userId,
        ExceptionRecord exceptionRecord,
        CaseUpdateDetails caseUpdateDetails,
        StartEventResponse eventResponse
    ) {
        CaseDetails existingCase = eventResponse.getCaseDetails();

        try {
            coreCaseDataApi.submitForCaseworker(
                idamToken,
                s2sToken,
                userId,
                exceptionRecord.poBoxJurisdiction,
                eventResponse.getCaseDetails().getCaseTypeId(),
                ignoreWarnings,
                getCaseDataContent(exceptionRecord, caseUpdateDetails, eventResponse)
            );

            log.info(
                "Successfully updated case for service {} with case Id {} based on exception record ref {}",
                service,
                existingCase.getId(),
                exceptionRecord.id
            );
        } catch (FeignException exception) {
            log.error(
                "Failed to update case for {} jurisdiction with case Id {} based on exception record with Id {}. "
                    + "Service response: {}",
                exceptionRecord.poBoxJurisdiction,
                existingCase.getId(),
                exceptionRecord.id,
                exception.contentUTF8(),
                exception
            );

            throw exception;
        } catch (Exception exception) {
            log.error(
                "Failed to update case for {} jurisdiction with case Id {} based on exception record with Id {}",
                exceptionRecord.poBoxJurisdiction,
                existingCase.getId(),
                exceptionRecord.id,
                exception
            );

            throw exception;
        }
    }

    private CaseDataContent getCaseDataContent(
        ExceptionRecord exceptionRecord,
        CaseUpdateDetails caseUpdateDetails,
        StartEventResponse eventResponse
    ) {
        return CaseDataContent
            .builder()
            .caseReference(exceptionRecord.id)
            .data(caseUpdateDetails.caseData)
            .event(getEvent(exceptionRecord, eventResponse.getCaseDetails().getId(), eventResponse.getEventId()))
            .eventToken(eventResponse.getToken())
            .build();
    }

    private Event getEvent(
        ExceptionRecord exceptionRecord,
        Long existingCaseId,
        String eventId
    ) {
        return Event
            .builder()
            .id(eventId)
            .summary(format("Case updated, case Id %s", existingCaseId))
            .description(
                format(
                    "Case with case Id %s updated based on exception record ref %s",
                    existingCaseId,
                    exceptionRecord.id
                )
            )
            .build();
    }
}
