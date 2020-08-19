package uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.web.client.RestTemplate;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGenerator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.request.TransformationRequest;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.response.CaseCreationDetails;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.response.SuccessfulTransformationResponse;

import javax.validation.ConstraintViolationException;
import javax.validation.Validation;
import javax.validation.Validator;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/*
Test only focuses on the extra functionality: validation.
The rest and all unchanged functionality is tested in integration test using wiremock
 */
@ExtendWith(MockitoExtension.class)
class TransformationClientTest {

    private static final Validator validator = Validation
        .buildDefaultValidatorFactory()
        .getValidator();
    private static final String URL = "http://url";

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private AuthTokenGenerator s2sTokenGenerator;

    private TransformationClient transformationClient;

    @BeforeEach
    void setUp() {
        transformationClient = new TransformationClient(restTemplate, validator, s2sTokenGenerator);
    }

    @Test
    void should_send_transformation_request_using_rest_template() {
        // given
        TransformationRequest transformationRequest = mock(TransformationRequest.class);

        SuccessfulTransformationResponse expectedTransformationResponse = sampleTransformationResponse();
        given(restTemplate.postForObject(anyString(), any(), any())).willReturn(expectedTransformationResponse);

        // when
        transformationClient.transformCaseData(URL, transformationRequest);

        // then
        var requestCaptor = ArgumentCaptor.forClass(HttpEntity.class);

        verify(restTemplate).postForObject(
            eq(URL),
            requestCaptor.capture(),
            eq(SuccessfulTransformationResponse.class)
        );

        assertThat(requestCaptor.getValue().getBody()).isSameAs(transformationRequest);
    }

    @Test
    void should_return_valid_model() {
        given(restTemplate.postForObject(anyString(), any(), any()))
            .willReturn(sampleTransformationResponse());

        assertThatCode(() -> transformationClient.transformCaseData(URL, null))
            .doesNotThrowAnyException();
    }

    @Test
    void should_throw_exception_when_model_is_invalid() {
        given(restTemplate.postForObject(anyString(), any(), any()))
            .willReturn(new SuccessfulTransformationResponse(null, emptyList()));

        assertThatCode(() -> transformationClient.transformCaseData(URL, null))
            .isInstanceOf(ConstraintViolationException.class)
            .hasMessage("caseCreationDetails: must not be null");
    }

    private SuccessfulTransformationResponse sampleTransformationResponse() {
        return new SuccessfulTransformationResponse(
            new CaseCreationDetails(
                "case type id",
                "event id",
                singletonMap("key", "value")
            ),
            emptyList()
        );
    }
}
