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

    private static final ProcessResult NO_ERRORS_OR_WARNINGS_PROCESS_RESULT =
        new ProcessResult(emptyList(), emptyList());

    private final AuthTokenGenerator s2sTokenGenerator;
    private final CoreCaseDataApi coreCaseDataApi;
    private final CaseUpdateClient caseUpdateClient;
    private final ServiceResponseParser serviceResponseParser;

    public CcdCaseUpdater(
        AuthTokenGenerator s2sTokenGenerator,
        CoreCaseDataApi coreCaseDataApi,
        CaseUpdateClient caseUpdateClient,
        ServiceResponseParser serviceResponseParser
    ) {
        this.s2sTokenGenerator = s2sTokenGenerator;
        this.coreCaseDataApi = coreCaseDataApi;
        this.caseUpdateClient = caseUpdateClient;
        this.serviceResponseParser = serviceResponseParser;
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
                return NO_ERRORS_OR_WARNINGS_PROCESS_RESULT;
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
                    ignoreWarnings,
                    new CcdRequestCredentials(idamToken, s2sToken, userId),
                    existingCaseId,
                    exceptionRecord,
                    updateResponse.caseDetails,
                    startEvent
                );

                log.info(
                    "Successfully updated case for service {} with case Id {} based on exception record ref {}",
                    configItem.getService(),
                    existingCase.getId(),
                    exceptionRecord.id
                );

                return NO_ERRORS_OR_WARNINGS_PROCESS_RESULT;
            }
        } catch (UnprocessableEntity exception) {
            ClientServiceErrorResponse errorResponse = serviceResponseParser.parseResponseBody(exception);
            return new ProcessResult(errorResponse.warnings, errorResponse.errors);
        } catch (FeignException.Conflict exception) {
            String msg = getErrorMessage(configItem.getService(), existingCaseId, exceptionRecord.id)
                + " because it has been updated in the meantime";
            log.error(msg);
            ClientServiceErrorResponse errorResponse = new ClientServiceErrorResponse(singletonList(msg), emptyList());
            return new ProcessResult(errorResponse.warnings, errorResponse.errors);
        } catch (FeignException.NotFound exception) {
            String msg = "No case found for case ID: " + existingCaseId;
            log.error(
                "No case found for case ID: {} service: {} exception record id: {}",
                existingCaseId, configItem.getService(), exceptionRecord.id
            );
            ClientServiceErrorResponse errorResponse = new ClientServiceErrorResponse(singletonList(msg), emptyList());
            return new ProcessResult(errorResponse.warnings, errorResponse.errors);
        } catch (FeignException.BadRequest exception) {
            String msg = "Invalid case ID: " + existingCaseId;
            log.error(
                "Invalid case ID: {} service: {} exception record id: {}",
                existingCaseId, configItem.getService(), exceptionRecord.id, exception
            );
            ClientServiceErrorResponse errorResponse = new ClientServiceErrorResponse(singletonList(msg), emptyList());
            return new ProcessResult(errorResponse.warnings, errorResponse.errors);
        } catch (FeignException exception) {
            debugCcdException(log, exception, "Failed to call 'updateCase'");
            log.error(
                getErrorMessage(configItem.getService(), existingCaseId, exceptionRecord.id)
                    + "Service response: {}",
                configItem.getService(),
                existingCaseId,
                exceptionRecord.id,
                exception.contentUTF8(),
                exception
            );

            throw new CallbackException(
                String.format(
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
        return String.format(
            "Failed to update case for %s service with case Id %s based on exception record %s",
            service,
            existingCaseId,
            exceptionRecordId
        );
    }

    /**
     * Submits event to update the case.
     */
    @SuppressWarnings("squid:S00107") // number of params
    private void updateCaseInCcd(
        boolean ignoreWarnings,
        CcdRequestCredentials ccdRequestCredentials,
        String existingCaseId,
        ExceptionRecord exceptionRecord,
        CaseUpdateDetails caseUpdateDetails,
        StartEventResponse startEvent
    ) {
        CaseDetails existingCase = startEvent.getCaseDetails();

        final CaseDataContent caseDataContent = buildCaseDataContent(exceptionRecord, caseUpdateDetails, startEvent);
        try {
            coreCaseDataApi.submitEventForCaseWorker(
                ccdRequestCredentials.idamToken,
                ccdRequestCredentials.s2sToken,
                ccdRequestCredentials.userId,
                exceptionRecord.poBoxJurisdiction,
                startEvent.getCaseDetails().getCaseTypeId(),
                existingCaseId,
                ignoreWarnings,
                caseDataContent
            );
        } catch (FeignException.UnprocessableEntity exception) {
            String msg = String.format(
                "CCD returned 422 Unprocessable Entity response "
                    + "when trying to update case for %s jurisdiction "
                    + "with case Id %s "
                    + "based on exception record with Id %s. "
                    + "CCD response: %s",
                exceptionRecord.poBoxJurisdiction,
                existingCase.getId(),
                exceptionRecord.id,
                exception.contentUTF8()
            );
            throw new CcdCallException(msg, exception);
        } catch (FeignException.Conflict exception) {
            throw exception;
        } catch (FeignException exception) {
            debugCcdException(log, exception, "Failed to call 'updateCaseInCcd'");
            // should service response be removed?
            String msg = String.format("Service response: %s", exception.contentUTF8());
            log.error(
                "Failed to update case for {} jurisdiction "
                    + "with case Id {} "
                    + "based on exception record with Id {}. "
                    + "{}",
                exceptionRecord.poBoxJurisdiction,
                existingCase.getId(),
                exceptionRecord.id,
                msg,
                exception
            );

            throw new RuntimeException(msg);
        }
    }

    private CaseDataContent buildCaseDataContent(
        ExceptionRecord exceptionRecord,
        CaseUpdateDetails caseUpdateDetails,
        StartEventResponse startEvent
    ) {
        return CaseDataContent
            .builder()
            .caseReference(exceptionRecord.id)
            .data(caseUpdateDetails.caseData)
            .event(Event
                .builder()
                .id(startEvent.getEventId())
                .summary(String.format("Case updated, case Id %s", startEvent.getCaseDetails().getId()))
                .description(String.format("Case updated based on exception record ref %s", exceptionRecord.id))
                .build()
            )
            .eventToken(startEvent.getToken())
            .build();
    }
}
