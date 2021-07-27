package uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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

    private final RestTemplate restTemplate;
    private final Validator validator;
    private final AuthTokenGenerator s2sTokenGenerator;
    private final ObjectMapper objectMapper;

    public TransformationClient(
        RestTemplate restTemplate,
        Validator validator,
        AuthTokenGenerator s2sTokenGenerator
    ) {
        this.restTemplate = restTemplate;
        this.validator = validator;
        this.s2sTokenGenerator = s2sTokenGenerator;
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    public SuccessfulTransformationResponse transformCaseData(
        String baseUrl,
        TransformationRequest transformationRequest
    ) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("ServiceAuthorization", s2sTokenGenerator.generate());
        headers.add("Content-Type", APPLICATION_JSON.toString());
        try {
            if (transformationRequest != null) {
                var req = objectMapper.writeValueAsString(transformationRequest);
                log.info(
                    "Exception id={}, TransformationRequest ===>{}",
                    transformationRequest.exceptionRecordCaseTypeId,
                    req
                );
            } else {
                log.info("TransformationRequest ===> null");
            }
        } catch (JsonProcessingException e) {
            log.error("Error transformationRequest writeValueAsString ", e);
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
