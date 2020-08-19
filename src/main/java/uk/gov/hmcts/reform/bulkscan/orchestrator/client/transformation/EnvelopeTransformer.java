package uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation;

import io.vavr.control.Either;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.TransformationRequestCreator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.request.TransformationRequest;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.response.SuccessfulTransformationResponse;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.config.ServiceConfigProvider;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Envelope;

import javax.validation.ConstraintViolationException;

import static io.vavr.control.Either.left;
import static io.vavr.control.Either.right;
import static java.lang.String.format;

@Service
public class EnvelopeTransformer {

    private static final Logger log = LoggerFactory.getLogger(EnvelopeTransformer.class);

    private final TransformationRequestCreator requestCreator;
    private final TransformationClient transformationClient;
    private final ServiceConfigProvider serviceConfigProvider;

    public EnvelopeTransformer(
        TransformationRequestCreator requestCreator,
        TransformationClient transformationClient,
        ServiceConfigProvider serviceConfigProvider
    ) {
        this.requestCreator = requestCreator;
        this.transformationClient = transformationClient;
        this.serviceConfigProvider = serviceConfigProvider;
    }

    public Either<TransformationFailureType, SuccessfulTransformationResponse> transformEnvelope(
        Envelope envelope
    ) {
        String loggingContext = getLoggingContext(envelope);

        try {
            log.info("About to transform envelope. {}", loggingContext);

            String transformationUrl = serviceConfigProvider.getConfig(envelope.container).getTransformationUrl();
            TransformationRequest transformationRequest = requestCreator.create(envelope);

            SuccessfulTransformationResponse transformationResponse =
                transformationClient.transformCaseData(transformationUrl, transformationRequest);

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

    private String getLoggingContext(Envelope envelope) {
        return format(
            "Envelope ID: %s. File name: %s. Service: %s.",
            envelope.id,
            envelope.zipFileName,
            envelope.container
        );
    }

    public enum TransformationFailureType {
        POTENTIALLY_RECOVERABLE,
        UNRECOVERABLE
    }
}
