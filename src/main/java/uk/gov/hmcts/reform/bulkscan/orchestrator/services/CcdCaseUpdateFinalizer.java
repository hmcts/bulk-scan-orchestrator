package uk.gov.hmcts.reform.bulkscan.orchestrator.services;

import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.caseupdate.model.response.CaseUpdateDetails;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.request.ExceptionRecord;
import uk.gov.hmcts.reform.ccd.client.CoreCaseDataApi;
import uk.gov.hmcts.reform.ccd.client.model.CaseDataContent;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.ccd.client.model.Event;
import uk.gov.hmcts.reform.ccd.client.model.StartEventResponse;

import java.util.Optional;

import static java.lang.String.format;

@Component
public class CcdCaseUpdateFinalizer {
    private static final Logger log = LoggerFactory.getLogger(CcdCaseUpdateFinalizer.class);

    private final CoreCaseDataApi coreCaseDataApi;

    public CcdCaseUpdateFinalizer(CoreCaseDataApi coreCaseDataApi) {
        this.coreCaseDataApi = coreCaseDataApi;
    }

    /**
     * Submits event to update the case.
     *
     * @return either error message in case of error or empty if no error detected
     */
    public Optional<String> updateCaseInCcd(
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
        try {
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

            return Optional.empty();
        } catch (FeignException.UnprocessableEntity exception) {
            String msg = format(
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
            log.error(msg, exception);

            return Optional.of(msg);
        } catch (FeignException.Conflict exception) {
            throw exception;
        } catch (FeignException exception) {
            String msg = format("Service response: %s", exception.contentUTF8());
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
