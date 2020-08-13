package uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.TransformationRequestCreator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.request.TransformationRequest;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.response.SuccessfulTransformationResponse;
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.ServiceConfigItem;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.config.ServiceConfigProvider;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Envelope;

import java.time.Instant;
import javax.validation.ConstraintViolationException;

import static io.vavr.control.Either.left;
import static io.vavr.control.Either.right;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class EnvelopeTransformerTest {

    @Mock
    private TransformationRequestCreator requestCreator;

    @Mock
    private TransformationClient transformationClient;

    @Mock
    private ServiceConfigProvider serviceConfigProvider;

    @Mock
    private ServiceConfigItem serviceConfigItem;

    private EnvelopeTransformer envelopeTransformer;

    @BeforeEach
    void setUp() {
        given(serviceConfigProvider.getConfig(any())).willReturn(serviceConfigItem);

        envelopeTransformer = new EnvelopeTransformer(
            requestCreator,
            transformationClient,
            serviceConfigProvider
        );
    }

    @Test
    void transformEnvelope_should_call_transformation_client_and_return_its_result() {
        // given
        Envelope envelope = sampleEnvelope();

        TransformationRequest transformationRequest = mock(TransformationRequest.class);
        given(requestCreator.create(ArgumentMatchers.<Envelope>any())).willReturn(transformationRequest);

        SuccessfulTransformationResponse expectedResponse = mock(SuccessfulTransformationResponse.class);
        given(transformationClient.transformCaseData(any(), any())).willReturn(expectedResponse);

        String transformationUrl = "transformationUrl1";
        given(serviceConfigItem.getTransformationUrl()).willReturn(transformationUrl);

        // when
        var result = envelopeTransformer.transformEnvelope(envelope);

        // then
        assertThat(result).isEqualTo(right(expectedResponse));
        verify(requestCreator).create(envelope);
        verify(transformationClient).transformCaseData(transformationUrl, transformationRequest);
        verify(serviceConfigProvider).getConfig(envelope.container);
    }

    @Test
    void should_return_failure_when_transformation_response_is_malformed() {
        verifyCorrectResultIsReturnedWhenClientThrowsException(
            new ConstraintViolationException("test", emptySet()),
            EnvelopeTransformer.TransformationFailureType.UNRECOVERABLE
        );
    }

    @Test
    void should_return_failure_when_transformation_results_in_bad_request_response() {
        verifyCorrectResultIsReturnedWhenClientThrowsException(
            transformationErrorResponseException(HttpStatus.BAD_REQUEST),
            EnvelopeTransformer.TransformationFailureType.UNRECOVERABLE
        );
    }

    @Test
    void should_return_failure_when_transformation_results_in_unprocessable_entity_response() {
        verifyCorrectResultIsReturnedWhenClientThrowsException(
            transformationErrorResponseException(HttpStatus.UNPROCESSABLE_ENTITY),
            EnvelopeTransformer.TransformationFailureType.UNRECOVERABLE
        );
    }

    @Test
    void should_return_failure_when_transformation_results_in_other_error_response() {
        verifyCorrectResultIsReturnedWhenClientThrowsException(
            transformationErrorResponseException(HttpStatus.INTERNAL_SERVER_ERROR),
            EnvelopeTransformer.TransformationFailureType.POTENTIALLY_RECOVERABLE
        );
    }

    @Test
    void should_return_failure_when_case_data_transformer_throws_other_exception() {
        verifyCorrectResultIsReturnedWhenClientThrowsException(
            new RuntimeException("test"),
            EnvelopeTransformer.TransformationFailureType.POTENTIALLY_RECOVERABLE
        );
    }

    private void verifyCorrectResultIsReturnedWhenClientThrowsException(
        Exception transformationClientException,
        EnvelopeTransformer.TransformationFailureType expectedFailureType
    ) {
        // given
        given(requestCreator.create(ArgumentMatchers.<Envelope>any())).willReturn(
            mock(TransformationRequest.class)
        );

        willThrow(transformationClientException)
            .given(transformationClient)
            .transformCaseData(any(), any());

        // when
        var result = envelopeTransformer.transformEnvelope(sampleEnvelope());

        // then
        assertThat(result).isEqualTo(left(expectedFailureType));

        verify(transformationClient).transformCaseData(any(), any());
    }


    private HttpClientErrorException transformationErrorResponseException(HttpStatus status) {
        return HttpClientErrorException.create(status, "test", HttpHeaders.EMPTY, null, null);
    }

    private Envelope sampleEnvelope() {
        return new Envelope(
            "envelopeId1",
            "caseRef1",
            "legacyCaseRef1",
            "poBox1",
            "jurisdiction1",
            "service1",
            "zipFileName1",
            "formType1",
            Instant.now().minusSeconds(1),
            Instant.now(),
            Classification.NEW_APPLICATION,
            emptyList(),
            emptyList(),
            emptyList(),
            emptyList()
        );
    }
}
