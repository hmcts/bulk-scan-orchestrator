package uk.gov.hmcts.reform.bulkscan.orchestrator.logging;

import feign.FeignException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;

import java.nio.ByteBuffer;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.times;
import static org.mockito.BDDMockito.verify;
import static org.mockito.Mockito.never;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.logging.FeignExceptionLogger.debugCcdException;

@ExtendWith(MockitoExtension.class)
class FeignExceptionLoggerTest {

    @Mock
    private Logger logger;

    @Mock
    private FeignException exception;

    @Test
    void should_pick_from_response_body_when_it_is_present() {
        // given
        given(exception.responseBody()).willReturn(Optional.of(ByteBuffer.wrap("response body".getBytes())));

        // when
        debugCcdException(logger, exception, "Intro");

        // then
        verify(exception, times(1)).responseBody();
        verify(exception, never()).getMessage();

        // and
        var logParamCaptor = ArgumentCaptor.forClass(String.class);

        verify(logger).debug(logParamCaptor.capture(), logParamCaptor.capture(), logParamCaptor.capture());

        assertThat(logParamCaptor.getAllValues())
            .hasSize(3)
            .containsOnly("{}. CCD response: {}", "Intro", "response body");
    }

    @Test
    void should_pick_exception_message_when_response_body_is_not_present() {
        // given
        given(exception.responseBody()).willReturn(Optional.empty());
        given(exception.getMessage()).willReturn("error message");

        // when
        debugCcdException(logger, exception, "Intro");

        // then
        verify(exception, times(1)).responseBody();
        verify(exception, times(1)).getMessage();

        // and
        var logParamCaptor = ArgumentCaptor.forClass(String.class);

        verify(logger).debug(logParamCaptor.capture(), logParamCaptor.capture(), logParamCaptor.capture());

        assertThat(logParamCaptor.getAllValues())
            .hasSize(3)
            .containsOnly("{}. CCD response: {}", "Intro", "error message");
    }
}
