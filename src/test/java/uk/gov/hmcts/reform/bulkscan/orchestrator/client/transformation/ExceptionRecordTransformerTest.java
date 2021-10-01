package uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.HttpClientErrorException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.request.TransformationRequest;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.response.SuccessfulTransformationResponse;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.internal.ExceptionRecord;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ExceptionRecordTransformerTest {

    @Mock
    private TransformationRequestCreator requestCreator;

    @Mock
    private TransformationClient transformationClient;

    private ExceptionRecordTransformer exceptionRecordTransformer;

    @BeforeEach
    void setUp() {
        exceptionRecordTransformer = new ExceptionRecordTransformer(requestCreator, transformationClient);
    }

    @Test
    void transformExceptionRecord_should_call_transformation_client_and_return_its_result() {
        // given
        ExceptionRecord exceptionRecord = mock(ExceptionRecord.class);
        TransformationRequest transformationRequest = mock(TransformationRequest.class);

        given(requestCreator.create(ArgumentMatchers.<ExceptionRecord>any(), anyBoolean()))
            .willReturn(transformationRequest);

        SuccessfulTransformationResponse expectedResponse = mock(SuccessfulTransformationResponse.class);
        given(transformationClient.transformCaseData(any(), any())).willReturn(expectedResponse);

        String baseUrl = "baseUrl1";
        String s2sToken = "s2sToken1";

        // when
        var result = exceptionRecordTransformer.transformExceptionRecord(baseUrl, exceptionRecord, false);

        // then
        assertThat(result).isEqualTo(expectedResponse);
        verify(requestCreator).create(exceptionRecord, false);
        verify(transformationClient).transformCaseData(baseUrl, transformationRequest);
    }

    @Test
    void transformExceptionRecord_should_rethrow_exception_when_client_fails() {
        HttpClientErrorException.BadRequest expectedException = mock(HttpClientErrorException.BadRequest.class);

        willThrow(expectedException).given(transformationClient).transformCaseData(any(), any());

        // when
        assertThatThrownBy(() ->
            exceptionRecordTransformer.transformExceptionRecord("baseUrl1", mock(ExceptionRecord.class), true)
        )
            .isSameAs(expectedException);
    }
}
