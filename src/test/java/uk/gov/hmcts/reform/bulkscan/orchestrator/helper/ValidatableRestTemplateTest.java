package uk.gov.hmcts.reform.bulkscan.orchestrator.helper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import javax.validation.ConstraintViolationException;
import javax.validation.Valid;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.constraints.NotNull;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ValidatableRestTemplateTest {

    private static final Validator validator = Validation
        .buildDefaultValidatorFactory()
        .getValidator();
    private static final String URL = "/url";

    @Mock
    private RestTemplate restTemplate;

    private ValidatableRestTemplate validatableRestTemplate;

    @BeforeEach
    void setUp() {
        validatableRestTemplate = new ValidatableRestTemplate(restTemplate, validator);
    }

    @Test
    void should_return_valid_model() {
        given(restTemplate.postForObject(URL, null, Model.class)).willReturn(new Model(new Object()));

        assertThatCode(() -> validatableRestTemplate.post(URL, null, Model.class))
            .doesNotThrowAnyException();
    }

    @Test
    void should_throw_exception_when_model_is_invalid() {
        given(restTemplate.postForObject(URL, null, Model.class)).willReturn(new Model(null));

        assertThatCode(() -> validatableRestTemplate.post(URL, null, Model.class))
            .isInstanceOf(ConstraintViolationException.class)
            .hasMessage("object: must not be null");
    }

    @Valid
    private static class Model {

        @NotNull
        public final Object object;

        private Model(Object object) {
            this.object = object;
        }
    }
}
