package uk.gov.hmcts.reform.bulkscan.orchestrator.logging;

import feign.FeignException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.verify;
import static org.mockito.Mockito.mock;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.logging.FeignExceptionLogger.debugCcdException;

@ExtendWith(MockitoExtension.class)
class FeignExceptionLoggerTest {

    @Mock
    private Logger logger;

    @Test
    void should_pick_from_response_body_when_it_is_present() {
        // given
        var exception = mock(FeignException.BadRequest.class);
        given(exception.getMessage()).willReturn("response body");
        // when
        debugCcdException(logger, exception, "Intro");

        // then
        var logParamCaptor = ArgumentCaptor.forClass(String.class);

        verify(logger).info(logParamCaptor.capture(), logParamCaptor.capture(), logParamCaptor.capture());

        assertThat(logParamCaptor.getAllValues())
            .hasSize(3)
            .containsOnly("{}. CCD response: {}", "Intro", "response body");
    }

    @Test
    void should_pick_exception_message_when_response_body_is_not_present() {
        // given
        var exception = mock(FeignException.BadRequest.class);
        given(exception.getMessage()).willReturn("error message");
        // when
        debugCcdException(logger, exception, "Intro");

        // then
        var logParamCaptor = ArgumentCaptor.forClass(String.class);

        verify(logger).debug(logParamCaptor.capture(), logParamCaptor.capture(), logParamCaptor.capture());

        assertThat(logParamCaptor.getAllValues())
            .hasSize(3)
            .containsOnly("{}. CCD response: {}", "Intro", "error message");
    }
}
