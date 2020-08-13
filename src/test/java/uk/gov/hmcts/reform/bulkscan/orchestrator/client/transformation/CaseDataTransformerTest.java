package uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.HttpClientErrorException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.TransformationRequestCreator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.request.TransformationRequest;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.response.SuccessfulTransformationResponse;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.internal.ExceptionRecord;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Envelope;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CaseDataTransformerTest {

    @Mock
    private TransformationRequestCreator requestCreator;

    @Mock
    private TransformationClient transformationClient;

    private CaseDataTransformer caseDataTransformer;

    @BeforeEach
    void setUp() {
        caseDataTransformer = new CaseDataTransformer(requestCreator, transformationClient);
    }

    @Test
    void transformExceptionRecord_should_call_transformation_client_and_return_its_result() {
        // given
        ExceptionRecord exceptionRecord = mock(ExceptionRecord.class);
        TransformationRequest transformationRequest = mock(TransformationRequest.class);

        given(requestCreator.create(ArgumentMatchers.<ExceptionRecord>any())).willReturn(transformationRequest);

        SuccessfulTransformationResponse expectedResponse = mock(SuccessfulTransformationResponse.class);
        given(transformationClient.transformCaseData(any(), any(), any())).willReturn(expectedResponse);

        String baseUrl = "baseUrl1";
        String s2sToken = "s2sToken1";

        // when
        var result = caseDataTransformer.transformExceptionRecord(baseUrl, exceptionRecord, s2sToken);

        // then
        assertThat(result).isEqualTo(expectedResponse);
        verify(requestCreator).create(exceptionRecord);
        verify(transformationClient).transformCaseData(baseUrl, transformationRequest, s2sToken);
    }

    @Test
    void transformExceptionRecord_should_rethrow_exception_when_client_fails() {
        HttpClientErrorException.BadRequest expectedException = mock(HttpClientErrorException.BadRequest.class);

        willThrow(expectedException).given(transformationClient).transformCaseData(any(), any(), any());

        // when
        assertThatThrownBy(() ->
            caseDataTransformer.transformExceptionRecord(
                "baseUrl1",
                mock(ExceptionRecord.class),
                "s2sToken1"
            )
        )
            .isSameAs(expectedException);
    }

    @Test
    void transformEnvelope_should_call_transformation_client_and_return_its_result() {
        // given
        Envelope envelope = mock(Envelope.class);

        TransformationRequest transformationRequest = mock(TransformationRequest.class);
        given(requestCreator.create(ArgumentMatchers.<Envelope>any())).willReturn(transformationRequest);

        SuccessfulTransformationResponse expectedResponse = mock(SuccessfulTransformationResponse.class);
        given(transformationClient.transformCaseData(any(), any(), any())).willReturn(expectedResponse);

        String baseUrl = "baseUrl1";
        String s2sToken = "s2sToken1";

        // when
        var result = caseDataTransformer.transformEnvelope(baseUrl, envelope, s2sToken);

        // then
        assertThat(result).isEqualTo(expectedResponse);
        verify(requestCreator).create(envelope);
        verify(transformationClient).transformCaseData(baseUrl, transformationRequest, s2sToken);
    }

    @Test
    void transformEnvelope_should_rethrow_exception_when_client_fails() {
        HttpClientErrorException.BadRequest expectedException = mock(HttpClientErrorException.BadRequest.class);

        willThrow(expectedException).given(transformationClient).transformCaseData(any(), any(), any());

        // when
        assertThatThrownBy(() ->
            caseDataTransformer.transformEnvelope(
                "baseUrl1",
                mock(Envelope.class),
                "s2sToken1"
            )
        )
            .isSameAs(expectedException);
    }
}
