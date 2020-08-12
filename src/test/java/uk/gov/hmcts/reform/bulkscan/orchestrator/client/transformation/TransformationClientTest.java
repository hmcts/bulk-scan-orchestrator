package uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.web.client.RestTemplate;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.TransformationRequestCreator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.request.TransformationRequest;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.response.CaseCreationDetails;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.response.SuccessfulTransformationResponse;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.internal.ExceptionRecord;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Envelope;

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
    private TransformationRequestCreator requestCreator;

    private TransformationClient transformationClient;

    @BeforeEach
    void setUp() {
        transformationClient = new TransformationClient(restTemplate, validator, requestCreator);
    }

    @Test
    void transformExceptionRecord_should_use_request_creator_to_create_request_body() {
        // given
        TransformationRequest transformationRequest = mock(TransformationRequest.class);
        ExceptionRecord inputExceptionRecord = mock(ExceptionRecord.class);

        given(requestCreator.create(inputExceptionRecord)).willReturn(transformationRequest);

        SuccessfulTransformationResponse expectedTransformationResponse = sampleTransformationResponse();

        given(restTemplate.postForObject(anyString(), any(), any())).willReturn(expectedTransformationResponse);

        // when
        transformationClient.transformExceptionRecord(URL, inputExceptionRecord, "token123");

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
    void transformExceptionRecord_should_return_valid_model() {
        var expectedResponse = sampleTransformationResponse();

        given(restTemplate.postForObject(anyString(), any(), any())).willReturn(expectedResponse);

        var response = transformationClient.transformExceptionRecord(URL, null, null);
        assertThat(response).isSameAs(expectedResponse);
    }

    @Test
    void transformExceptionRecord_should_throw_exception_when_model_is_invalid() {
        given(restTemplate.postForObject(anyString(), any(), any()))
            .willReturn(new SuccessfulTransformationResponse(null, emptyList()));

        assertThatCode(() -> transformationClient.transformExceptionRecord(URL, null, null))
            .isInstanceOf(ConstraintViolationException.class)
            .hasMessage("caseCreationDetails: must not be null");
    }

    @Test
    void transformEnvelope_should_use_request_creator_to_create_request_body() {
        // given
        TransformationRequest transformationRequest = mock(TransformationRequest.class);
        Envelope inputEnvelope = mock(Envelope.class);
        given(requestCreator.create(inputEnvelope)).willReturn(transformationRequest);

        SuccessfulTransformationResponse expectedTransformationResponse = sampleTransformationResponse();
        given(restTemplate.postForObject(anyString(), any(), any())).willReturn(expectedTransformationResponse);

        // when
        transformationClient.transformEnvelope(URL, inputEnvelope, "token123");

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
    void transformEnvelope_should_return_response_when_valid() {
        var expectedResponse = sampleTransformationResponse();

        given(restTemplate.postForObject(anyString(), any(), any())).willReturn(expectedResponse);

        var response = transformationClient.transformEnvelope(URL, mock(Envelope.class), "s2sToken1");
        assertThat(response).isSameAs(expectedResponse);
    }

    @Test
    void transformEnvelope_should_throw_exception_when_response_is_invalid() {
        var responseWithNullDetails = new SuccessfulTransformationResponse(null, emptyList());
        given(restTemplate.postForObject(anyString(), any(), any())).willReturn(responseWithNullDetails);

        assertThatCode(() -> transformationClient.transformEnvelope(URL, mock(Envelope.class), "s2sToken1"))
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
