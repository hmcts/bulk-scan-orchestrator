package uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGenerator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.request.TransformationRequest;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.response.SuccessfulTransformationResponse;

import java.util.Set;
import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import javax.validation.Validator;

import static org.springframework.http.MediaType.APPLICATION_JSON;

@Service
public class TransformationClient {

    private static final Logger log = LoggerFactory.getLogger(TransformationClient.class);

    private final RestTemplate restTemplate;
    private final Validator validator;
    private final AuthTokenGenerator s2sTokenGenerator;

    public TransformationClient(
        RestTemplate restTemplate,
        Validator validator,
        AuthTokenGenerator s2sTokenGenerator
    ) {
        this.restTemplate = restTemplate;
        this.validator = validator;
        this.s2sTokenGenerator = s2sTokenGenerator;
    }

    public SuccessfulTransformationResponse transformCaseData(
        String baseUrl,
        TransformationRequest transformationRequest
    ) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("ServiceAuthorization", s2sTokenGenerator.generate());
        headers.add("Content-Type", APPLICATION_JSON.toString());

        if (transformationRequest != null) {
            log.info(
                "exceptionRecordId: {} ignoreWarnings: {}",
                transformationRequest.exceptionRecordId,
                transformationRequest.ignoreWarnings
            );
        }
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
