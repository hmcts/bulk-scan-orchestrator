package uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.TransformationRequestCreator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.request.TransformationRequest;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.response.SuccessfulTransformationResponse;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.internal.ExceptionRecord;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Envelope;

import java.util.Set;
import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import javax.validation.Validator;

@Component
public class TransformationClient {

    private final RestTemplate restTemplate;
    private final Validator validator;
    private final TransformationRequestCreator requestCreator;

    public TransformationClient(
        RestTemplate restTemplate,
        Validator validator,
        TransformationRequestCreator requestCreator
    ) {
        this.restTemplate = restTemplate;
        this.validator = validator;
        this.requestCreator = requestCreator;
    }

    public SuccessfulTransformationResponse transformEnvelope(String baseUrl, Envelope envelope, String s2sToken) {
        return callTransformationEndpoint(baseUrl, requestCreator.create(envelope), s2sToken);
    }

    public SuccessfulTransformationResponse transformExceptionRecord(
        String baseUrl,
        ExceptionRecord exceptionRecord,
        String s2sToken
    ) {
        return callTransformationEndpoint(baseUrl, requestCreator.create(exceptionRecord), s2sToken);
    }

    private SuccessfulTransformationResponse callTransformationEndpoint(
        String baseUrl,
        TransformationRequest transformationRequest,
        String s2sToken
    ) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("ServiceAuthorization", s2sToken);

        SuccessfulTransformationResponse response = restTemplate.postForObject(
            getUrl(baseUrl),
            new HttpEntity<>(transformationRequest, headers),
            SuccessfulTransformationResponse.class
        );

        Set<ConstraintViolation<SuccessfulTransformationResponse>> violations = validator.validate(response);

        if (violations.isEmpty()) {
            return response;
        }

        throw new ConstraintViolationException(violations);
    }

    private String getUrl(String transformationUrl) {
        return UriComponentsBuilder
            .fromHttpUrl(transformationUrl)
            .build()
            .toString();
    }
}
