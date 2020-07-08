package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException.UnprocessableEntity;
import org.springframework.web.client.RestClientException;
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

import java.util.List;
import javax.validation.ConstraintViolationException;

import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.helper.ScannedDocumentsHelper.getDocuments;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.helper.ScannedDocumentsHelper.setExceptionRecordIdToScannedDocuments;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.logging.FeignExceptionLogger.debugCcdException;
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
        String existingCaseId,
        String existingCaseTypeId
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
                existingCaseTypeId,
                existingCaseId,
                EVENT_ID_ATTACH_SCANNED_DOCS_WITH_OCR
            );

            log.info(
                "Started CCD event to update case. "
                    + "Event ID: {}. Case ID: {}. Exception record ID: {}. Case type: {}. Case state: {}",
                startEvent.getEventId(),
                existingCaseId,
                exceptionRecord.id,
                startEvent.getCaseDetails().getCaseTypeId(),
                startEvent.getCaseDetails().getState()
            );

            final CaseDetails existingCase = startEvent.getCaseDetails();

            if (isExceptionAlreadyAttached(existingCase, exceptionRecord)) {
                log.warn(
                    "Skipping Update as all documents from this exception record are already in the case. "
                        + "Exception record ID: {}, attempt to attach to case: {} ",
                    exceptionRecord.id,
                    existingCase.getId()
                );
                return new ProcessResult(emptyList(), emptyList());
            }

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
                setExceptionRecordIdToScannedDocuments(exceptionRecord, updateResponse.caseDetails);

                updateCaseInCcd(
                    configItem.getService(),
                    ignoreWarnings,
                    idamToken,
                    s2sToken,
                    userId,
                    existingCaseId,
                    exceptionRecord,
                    updateResponse.caseDetails,
                    startEvent
                );

                return new ProcessResult(
                    exceptionRecordFinalizer.finalizeExceptionRecord(
                        existingCase.getData(),
                        existingCase.getId(),
                        CcdCallbackType.ATTACHING_SUPPLEMENTARY_EVIDENCE
                    )
                );
            }
        } catch (UnprocessableEntity exception) {
            ClientServiceErrorResponse errorResponse = serviceResponseParser.parseResponseBody(exception);
            return new ProcessResult(errorResponse.warnings, errorResponse.errors);
        } catch (FeignException.UnprocessableEntity exception) {
            String msg = getErrorMessage(configItem.getService(), existingCaseId, exceptionRecord.id)
                + " - CCD could not process request";
            // is there a better way?
            boolean isStartEvent = exception.request().url().contains("event-triggers");

            log.error(msg, exception);

            if (!isStartEvent) {
                return new ProcessResult(singletonList(msg), emptyList());
            } else {
                throw new CallbackException(msg, exception);
            }
        } catch (FeignException.Conflict exception) {
            String msg = getErrorMessage(configItem.getService(), existingCaseId, exceptionRecord.id)
                + " because it has been updated in the meantime";
            log.error(msg, exception);
            ClientServiceErrorResponse errorResponse = new ClientServiceErrorResponse(singletonList(msg), emptyList());
            return new ProcessResult(errorResponse.warnings, errorResponse.errors);
        } catch (FeignException.NotFound exception) {
            String msg = "No case found for case ID: " + existingCaseId;
            log.error(
                "No case found for case ID: {} service: {} exception record id: {}",
                existingCaseId, configItem.getService(), exceptionRecord.id, exception
            );
            ClientServiceErrorResponse errorResponse = new ClientServiceErrorResponse(singletonList(msg), emptyList());
            return new ProcessResult(errorResponse.warnings, errorResponse.errors);
        } catch (FeignException.BadRequest exception) {
            // is there a better way?
            boolean isStartEvent = exception.request().url().contains("event-triggers");

            String msg = isStartEvent
                ? "Invalid case ID: " + existingCaseId
                : getErrorMessage(configItem.getService(), existingCaseId, exceptionRecord.id);

            log.error(
                "{}. Service: {}, exception record id: {}, response: {}",
                msg, configItem.getService(), exceptionRecord.id, exception.contentUTF8(), exception
            );

            ClientServiceErrorResponse errorResponse = new ClientServiceErrorResponse(singletonList(msg), emptyList());

            if (isStartEvent) {
                return new ProcessResult(errorResponse.warnings, errorResponse.errors);
            } else {
                throw new CallbackException(msg, exception);
            }
        } catch (FeignException exception) {
            debugCcdException(log, exception, "Failed to call 'updateCase'");
            log.error(
                getErrorMessage(configItem.getService(), existingCaseId, exceptionRecord.id),
                configItem.getService(),
                existingCaseId,
                exceptionRecord.id,
                exception
            );

            throw new CallbackException(
                format(
                    "%s. Service response: %s",
                    getErrorMessage(configItem.getService(), existingCaseId, exceptionRecord.id),
                    exception.contentUTF8()
                ),
                exception
            );
        // exceptions received from case update client
        } catch (RestClientException exception) {
            String message = getErrorMessage(configItem.getService(), existingCaseId, exceptionRecord.id);

            log.error(message, exception);

            throw new CallbackException(message, exception);
        // rest of exceptions we did not handle appropriately. so far not such case
        } catch (ConstraintViolationException exc) {
            String errorMessage = getErrorMessage(configItem.getService(), existingCaseId, exceptionRecord.id);
            throw new CallbackException(
                "Invalid case-update response. " + errorMessage,
                exc
            );
        } catch (Exception exception) {
            throw new CallbackException(
                getErrorMessage(configItem.getService(), existingCaseId, exceptionRecord.id),
                exception
            );
        }
    }

    private boolean isExceptionAlreadyAttached(
        CaseDetails existingCase,
        ExceptionRecord exceptionRecord
    ) {
        List<String> caseDocList = getDocuments(existingCase)
            .stream()
            .map(d -> d.controlNumber)
            .collect(toList());

        List<String> newExDocList = exceptionRecord
            .scannedDocuments
            .stream()
            .map(d -> d.controlNumber).collect(toList());

        return caseDocList.containsAll(newExDocList);
    }

    private String getErrorMessage(String service, String existingCaseId, String exceptionRecordId) {
        return format(
            "Failed to update case for %s service "
                + "with case Id %s "
                + "based on exception record %s",
            service,
            existingCaseId,
            exceptionRecordId
        );
    }

    /**
     * Submits event to update the case.
     */
    private void updateCaseInCcd(
        String service,
        boolean ignoreWarnings,
        String idamToken,
        String s2sToken,
        String userId,
        String existingCaseId,
        ExceptionRecord exceptionRecord,
        CaseUpdateDetails caseUpdateDetails,
        StartEventResponse startEvent
    ) {
        CaseDetails existingCase = startEvent.getCaseDetails();

        final CaseDataContent caseDataContent = getCaseDataContent(exceptionRecord, caseUpdateDetails, startEvent);

        coreCaseDataApi.submitEventForCaseWorker(
            idamToken,
            s2sToken,
            userId,
            exceptionRecord.poBoxJurisdiction,
            startEvent.getCaseDetails().getCaseTypeId(),
            existingCaseId,
            ignoreWarnings,
            caseDataContent
        );

        log.info(
            "Successfully updated case for service {} "
                + "with case Id {} "
                + "based on exception record ref {}",
            service,
            existingCase.getId(),
            exceptionRecord.id
        );
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
