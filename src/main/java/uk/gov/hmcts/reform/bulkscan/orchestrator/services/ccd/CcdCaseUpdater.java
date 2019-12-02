package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGenerator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.InvalidCaseDataException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.caseupdate.CaseUpdateClient;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.caseupdate.model.response.CaseUpdateDetails;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.caseupdate.model.response.SuccessfulUpdateResponse;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.request.ExceptionRecord;
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
import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Service
public class CcdCaseUpdater {
    private static final Logger log = LoggerFactory.getLogger(CcdCaseUpdater.class);

    private static final String EXCEPTION_RECORD_REFERENCE = "bulkScanCaseReference";

    private final CaseUpdateClient caseUpdateClient;
    private final AuthTokenGenerator s2sTokenGenerator;
    private final CoreCaseDataApi coreCaseDataApi;
    private final ExceptionRecordFinalizer exceptionRecordFinalizer;

    public CcdCaseUpdater(
        CaseUpdateClient caseUpdateClient,
        AuthTokenGenerator s2sTokenGenerator,
        CoreCaseDataApi coreCaseDataApi,
        ExceptionRecordFinalizer exceptionRecordFinalizer
    ) {
        this.caseUpdateClient = caseUpdateClient;
        this.s2sTokenGenerator = s2sTokenGenerator;
        this.coreCaseDataApi = coreCaseDataApi;
        this.exceptionRecordFinalizer = exceptionRecordFinalizer;
    }

    @SuppressWarnings({"squid:S2139", "unchecked"}) // squid for exception handle + logging
    public ProcessResult updateCase(
        ExceptionRecord exceptionRecord,
        ServiceConfigItem configItem,
        boolean ignoreWarnings,
        String idamToken,
        String userId,
        CaseDetails existingCase
    ) {
        log.info(
            "Start updating case for service {} with case ID {} from exception record {}",
            configItem.getService(),
            existingCase.getId(),
            exceptionRecord.id
        );

        try {
            String s2sToken = s2sTokenGenerator.generate();

            SuccessfulUpdateResponse updateResponse = caseUpdateClient.updateCase(
                configItem.getUpdateUrl(),
                existingCase,
                exceptionRecord,
                s2sToken
            );

            if (!ignoreWarnings && !updateResponse.warnings.isEmpty()) {
                // do not log warnings
                return new ProcessResult(updateResponse.warnings, emptyList());
            }

            log.info(
                "Successfully called service {} update endpoint with case ID {} from exception record {}",
                configItem.getService(),
                existingCase.getId(),
                exceptionRecord.id
            );

            updateCaseInCcd(
                ignoreWarnings,
                idamToken,
                s2sToken,
                userId,
                exceptionRecord,
                updateResponse.caseDetails,
                existingCase
            );

            log.info(
                "Successfully updated case for service {} with case ID {} from exception record {}",
                configItem.getService(),
                existingCase.getId(),
                exceptionRecord.id
            );

            return new ProcessResult(
                exceptionRecordFinalizer.finalizeExceptionRecord(existingCase.getData(), existingCase.getId())
            );
        } catch (InvalidCaseDataException exception) {
            if (BAD_REQUEST.equals(exception.getStatus())) {
                throw new CallbackException("Failed to update case", exception);
            } else {
                return new ProcessResult(exception.getResponse().warnings, exception.getResponse().errors);
            }
        } catch (Exception exception) {
            throw new CallbackException("Failed to update case", exception);
        }
    }

    @SuppressWarnings("squid:S2139") // exception handle + logging
    private long updateCaseInCcd(
        boolean ignoreWarnings,
        String idamToken,
        String s2sToken,
        String userId,
        ExceptionRecord exceptionRecord,
        CaseUpdateDetails caseUpdateDetails,
        CaseDetails existingCase
    ) {
        try {
            StartEventResponse eventResponse = coreCaseDataApi.startForCaseworker(
                idamToken,
                s2sToken,
                userId,
                exceptionRecord.poBoxJurisdiction,
                existingCase.getCaseTypeId(),
                // when onboarding remind services to not configure about to submit callback for this event
                caseUpdateDetails.eventId
            );

            return coreCaseDataApi.submitForCaseworker(
                idamToken,
                s2sToken,
                userId,
                exceptionRecord.poBoxJurisdiction,
                existingCase.getCaseTypeId(),
                ignoreWarnings,
                CaseDataContent
                    .builder()
                    .caseReference(exceptionRecord.id)
                    .data(caseUpdateDetails.caseData)
                    .event(Event
                        .builder()
                        .id(eventResponse.getEventId())
                        .summary(format("Case updated, case ID %s", existingCase.getId()))
                        .description(
                            format(
                                "Case with case ID %s updated based on exception record ref %s",
                                existingCase.getId(),
                                exceptionRecord.id
                            )
                        )
                        .build()
                    )
                    .eventToken(eventResponse.getToken())
                    .build()
            ).getId();
        } catch (FeignException exception) {
            log.error(
                "Failed to update case for {} jurisdiction with case ID {} from exception record {}. "
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
                "Failed to update case for {} jurisdiction with case ID {} from exception record {}",
                exceptionRecord.poBoxJurisdiction,
                existingCase.getId(),
                exceptionRecord.id,
                exception
            );

            throw exception;
        }
    }
}
