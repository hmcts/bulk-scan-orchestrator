package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException.BadRequest;
import org.springframework.web.client.HttpClientErrorException.Conflict;
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
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.EventIdValidator.EVENT_ID_ATTACH_SCANNED_DOCS_WITH_OCR;

@Service
public class CcdCaseUpdater {
    private static final Logger log = LoggerFactory.getLogger(CcdCaseUpdater.class);

    private final AuthTokenGenerator s2sTokenGenerator;
    private final CoreCaseDataApi coreCaseDataApi;
    private final CaseUpdateClient caseUpdateClient;
    private final ServiceResponseParser serviceResponseParser;
    private final ExceptionRecordFinalizer exceptionRecordFinalizer;

    public CcdCaseUpdater(
        AuthTokenGenerator s2sTokenGenerator,
        CoreCaseDataApi coreCaseDataApi,
        CaseUpdateClient caseUpdateClient,
        ServiceResponseParser serviceResponseParser,
        ExceptionRecordFinalizer exceptionRecordFinalizer
    ) {
        this.s2sTokenGenerator = s2sTokenGenerator;
        this.coreCaseDataApi = coreCaseDataApi;
        this.caseUpdateClient = caseUpdateClient;
        this.serviceResponseParser = serviceResponseParser;
        this.exceptionRecordFinalizer = exceptionRecordFinalizer;
    }

    public ProcessResult updateCase(
        ExceptionRecord exceptionRecord,
        ServiceConfigItem configItem,
        boolean ignoreWarnings,
        String idamToken,
        String userId,
        String existingCaseId
    ) {
        log.info(
            "Start updating case for service {} with case Id {} from exception record {}",
            configItem.getService(),
            existingCaseId,
            exceptionRecord.id
        );

        try {
            String s2sToken = s2sTokenGenerator.generate();

            StartEventResponse startEvent = coreCaseDataApi.startEventForCaseWorker(
                idamToken,
                s2sToken,
                userId,
                exceptionRecord.poBoxJurisdiction,
                exceptionRecord.caseTypeId,
                existingCaseId,
                EVENT_ID_ATTACH_SCANNED_DOCS_WITH_OCR
            );

            final CaseDetails existingCase = startEvent.getCaseDetails();

            SuccessfulUpdateResponse updateResponse = caseUpdateClient.updateCase(
                configItem.getUpdateUrl(),
                existingCase,
                exceptionRecord,
                s2sToken
            );

            log.info(
                "Successfully called case update endpoint of service {} to update case with case Id {} "
                    + "based on exception record ref {}",
                configItem.getService(),
                existingCase.getId(),
                exceptionRecord.id
            );

            if (!ignoreWarnings && !updateResponse.warnings.isEmpty()) {
                log.info(
                    "Returned warnings after calling case update endpoint of service {} to update case with case Id {} "
                        + "based on exception record ref {}",
                    configItem.getService(),
                    existingCase.getId(),
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
                    startEvent
                );

                return new ProcessResult(
                    exceptionRecordFinalizer.finalizeExceptionRecord(
                        existingCase.getData(),
                        existingCase.getId()
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
        } catch (Conflict exception) {
            String msg = format(
                "Failed to update case for %s service with case Id %s based on exception record %s "
                    + "because it has been updated in the meantime",
                configItem.getService(),
                existingCaseId,
                exceptionRecord.id
            );
            log.error(msg);
            ClientServiceErrorResponse errorResponse = new ClientServiceErrorResponse(asList(msg), emptyList());
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
        StartEventResponse startEvent
    ) {
        CaseDetails existingCase = startEvent.getCaseDetails();

        try {
            coreCaseDataApi.submitForCaseworker(
                idamToken,
                s2sToken,
                userId,
                exceptionRecord.poBoxJurisdiction,
                startEvent.getCaseDetails().getCaseTypeId(),
                ignoreWarnings,
                getCaseDataContent(exceptionRecord, caseUpdateDetails, startEvent)
            );

            log.info(
                "Successfully updated case for service {} with case Id {} based on exception record ref {}",
                service,
                existingCase.getId(),
                exceptionRecord.id
            );
        } catch (FeignException exception) {
            String msg = format("Service response: %s", exception.contentUTF8());
            log.error(
                "Failed to update case for {} jurisdiction with case Id {} based on exception record with Id {}. {}",
                exceptionRecord.poBoxJurisdiction,
                existingCase.getId(),
                exceptionRecord.id,
                msg,
                exception
            );

            throw new RuntimeException(msg);
        }
    }

    private CaseDataContent getCaseDataContent(
        ExceptionRecord exceptionRecord,
        CaseUpdateDetails caseUpdateDetails,
        StartEventResponse startEvent
    ) {
        return CaseDataContent
            .builder()
            .caseReference(exceptionRecord.id)
            .data(caseUpdateDetails.caseData)
            .event(getEvent(exceptionRecord, startEvent.getCaseDetails().getId(), startEvent.getEventId()))
            .eventToken(startEvent.getToken())
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
                    "Case updated based on exception record ref %s",
                    exceptionRecord.id
                )
            )
            .build();
    }
}
