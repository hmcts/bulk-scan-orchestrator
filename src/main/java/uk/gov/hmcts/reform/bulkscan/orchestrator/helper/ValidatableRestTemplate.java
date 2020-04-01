package uk.gov.hmcts.reform.bulkscan.orchestrator.helper;

import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Set;
import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import javax.validation.Validator;

@Component
public class ValidatableRestTemplate {

    private final RestTemplate restTemplate;
    private final Validator validator;

    public ValidatableRestTemplate(RestTemplate restTemplate, Validator validator) {
        this.restTemplate = restTemplate;
        this.validator = validator;
    }

    public <T> T post(String url, @Nullable Object request, Class<T> responseType) {
        T response = restTemplate.postForObject(url, request, responseType);

        Set<ConstraintViolation<T>> violations = validator.validate(response);

        if (violations.isEmpty()) {
            return response;
        }

        throw new ConstraintViolationException(violations);
    }
}
