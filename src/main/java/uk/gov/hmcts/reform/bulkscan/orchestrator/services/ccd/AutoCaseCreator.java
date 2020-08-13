package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import feign.FeignException;
import io.vavr.control.Either;
import org.apache.logging.log4j.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.CaseDataTransformer;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.response.SuccessfulTransformationResponse;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.CaseAction;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.config.ServiceConfigProvider;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Envelope;
import uk.gov.hmcts.reform.ccd.client.model.CaseDataContent;
import uk.gov.hmcts.reform.ccd.client.model.Event;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.validation.ConstraintViolationException;

import static io.vavr.control.Either.left;
import static io.vavr.control.Either.right;
import static java.lang.String.format;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CaseCreationResult.abortedWithoutFailure;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CaseCreationResult.caseAlreadyExists;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CaseCreationResult.caseCreated;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CaseCreationResult.potentiallyRecoverableFailure;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CaseCreationResult.unrecoverableFailure;

@Service
public class AutoCaseCreator {

    private static final Logger log = LoggerFactory.getLogger(AutoCaseCreator.class);

    private final CaseDataTransformer caseDataTransformer;
    private final CcdApi ccdApi;
    private final ServiceConfigProvider serviceConfigProvider;
    private final EnvelopeReferenceCollectionHelper envelopeReferenceCollectionHelper;

    public AutoCaseCreator(
        CaseDataTransformer caseDataTransformer,
        CcdApi ccdApi,
        ServiceConfigProvider serviceConfigProvider,
        EnvelopeReferenceCollectionHelper envelopeReferenceCollectionHelper
    ) {
        this.caseDataTransformer = caseDataTransformer;
        this.ccdApi = ccdApi;
        this.serviceConfigProvider = serviceConfigProvider;
        this.envelopeReferenceCollectionHelper = envelopeReferenceCollectionHelper;
    }

    public CaseCreationResult createCase(Envelope envelope) {
        String loggingContext = getLoggingContext(envelope);
        log.info("Started attempt to create a new case from envelope. {}", loggingContext);

        if (serviceConfigProvider.getConfig(envelope.container).getAutoCaseCreationEnabled()) {
            return createCaseIfDoesNotExist(envelope, loggingContext);
        } else {
            log.info("Automatic case creation is disabled for the service - skipping. {}", loggingContext);
            return abortedWithoutFailure();
        }
    }

    private CaseCreationResult createCaseIfDoesNotExist(Envelope envelope, String loggingContext) {
        List<Long> caseIds = ccdApi.getCaseRefsByEnvelopeId(envelope.id, envelope.container);

        if (caseIds.isEmpty()) {
            return transformAndCreateCase(envelope, loggingContext);
        } else if (caseIds.size() == 1) {
            long caseId = caseIds.get(0);
            log.warn("Case already exists for envelope - skipping creation. Case ID: {}. {}", caseId, loggingContext);
            return caseAlreadyExists(caseId);
        } else {
            log.error(
                "Multiple cases exist for envelope. Case Ids: [{}]. {}",
                Strings.join(caseIds, ','),
                loggingContext
            );

            return unrecoverableFailure();
        }
    }

    private CaseCreationResult transformAndCreateCase(Envelope envelope, String loggingContext) {
        return transformEnvelope(envelope, loggingContext)
            .map(successfulTransformationResponse ->
                createCaseInCcd(successfulTransformationResponse, envelope, loggingContext)
            )
            .getOrElseGet(failureType ->
                failureType == TransformationFailureType.UNRECOVERABLE
                    ? unrecoverableFailure()
                    : potentiallyRecoverableFailure()
            );
    }

    private CaseCreationResult createCaseInCcd(
        SuccessfulTransformationResponse transformationResponse,
        Envelope envelope,
        String loggingContext
    ) {
        try {
            log.info("About to create a case in CCD from envelope. {}", loggingContext);
            long caseId = callCcdApiToCreateCase(transformationResponse, envelope, loggingContext);
            log.info("Created a case in CCD from envelope. Case Id: {}. {}", caseId, loggingContext);

            return caseCreated(caseId);
        } catch (FeignException.UnprocessableEntity | FeignException.BadRequest ex) {
            log.error(
                "Received a response with status {} when trying to create a CCD case from an envelope. {}",
                ex.status(),
                loggingContext,
                ex
            );

            return unrecoverableFailure();
        } catch (Exception ex) {
            log.error(
                "An error occurred when trying to create a case in CCD from envelope. {}",
                loggingContext,
                ex
            );

            return potentiallyRecoverableFailure();
        }
    }

    private long callCcdApiToCreateCase(
        SuccessfulTransformationResponse transformationResponse,
        Envelope envelope,
        String loggingContext
    ) {
        return ccdApi.createCase(
            envelope.jurisdiction,
            transformationResponse.caseCreationDetails.caseTypeId,
            transformationResponse.caseCreationDetails.eventId,
            startEventResponse ->
                getCaseDataContent(
                    transformationResponse.caseCreationDetails.caseData,
                    envelope.id,
                    startEventResponse.getEventId(),
                    startEventResponse.getToken()
                ),
            loggingContext
        );
    }

    private Either<TransformationFailureType, SuccessfulTransformationResponse> transformEnvelope(
        Envelope envelope,
        String loggingContext
    ) {
        try {
            log.info("About to transform envelope. {}", loggingContext);

            String transformationUrl = serviceConfigProvider.getConfig(envelope.container).getTransformationUrl();

            SuccessfulTransformationResponse transformationResponse =
                caseDataTransformer.transformEnvelope(transformationUrl, envelope);

            log.info("Received successful transformation response for envelope. {}", loggingContext);

            return right(transformationResponse);
        } catch (ConstraintViolationException ex) {
            logMalformedTransformationResponseError(ex, loggingContext);
            return left(TransformationFailureType.UNRECOVERABLE);
        } catch (HttpClientErrorException.BadRequest ex) {
            logBadRequestTransformationResponseError(ex, loggingContext);
            return left(TransformationFailureType.UNRECOVERABLE);
        } catch (HttpClientErrorException.UnprocessableEntity ex) {
            logUnprocessableEntityTransformationResponse(ex, loggingContext);
            return left(TransformationFailureType.UNRECOVERABLE);
        } catch (Exception ex) {
            log.error("An error occurred when transforming envelope into case data. {}", loggingContext, ex);
            return left(TransformationFailureType.POTENTIALLY_RECOVERABLE);
        }
    }

    private CaseDataContent getCaseDataContent(
        Map<String, Object> caseData,
        String envelopeId,
        String eventId,
        String eventToken
    ) {
        Map<String, Object> data = new HashMap<>(caseData);

        data.put(
            "bulkScanEnvelopes",
            envelopeReferenceCollectionHelper.singleEnvelopeReferenceList(envelopeId, CaseAction.CREATE)
        );

        return CaseDataContent
            .builder()
            .data(data)
            .event(Event
                .builder()
                .id(eventId)
                .summary("Case created")
                .description("Case created from envelope " + envelopeId)
                .build()
            )
            .eventToken(eventToken)
            .build();
    }

    private String getLoggingContext(Envelope envelope) {
        return format(
            "Envelope ID: %s. File name: %s. Service: %s.",
            envelope.id,
            envelope.zipFileName,
            envelope.container
        );
    }

    private void logUnprocessableEntityTransformationResponse(
        HttpClientErrorException.UnprocessableEntity exception,
        String loggingContext
    ) {
        log.info(
            "Received validation error response from transformation endpoint called for envelope. {}",
            loggingContext,
            exception
        );
    }

    private void logBadRequestTransformationResponseError(
        HttpClientErrorException.BadRequest exception,
        String loggingContext
    ) {
        String responseBody = exception.getResponseBodyAsString();
        log.error(
            "Received a response with status {} from transformation endpoint called for envelope. "
                + "{}. Response starts with: [{}]",
            exception.getRawStatusCode(),
            loggingContext,
            responseBody.substring(0, Math.min(10000, responseBody.length())),
            exception
        );
    }

    private void logMalformedTransformationResponseError(
        ConstraintViolationException exception,
        String loggingContext
    ) {
        log.error(
            "Received malformed response from transformation endpoint called for envelope. {} Violations: [{}].",
            loggingContext,
            exception.getMessage()
        );
    }

    private enum TransformationFailureType {
        POTENTIALLY_RECOVERABLE,
        UNRECOVERABLE
    }
}
