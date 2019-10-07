package uk.gov.hmcts.reform.bulkscan.orchestrator.controllers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.servlet.http.HttpServletRequest;

import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
public class CallbackInterceptorTest {

    private CallbackInterceptor callbackInterceptor;

    @Mock
    private HttpServletRequest req;

    @BeforeEach
    public void setUp() {
        callbackInterceptor = new CallbackInterceptor();
    }

    @Test
    public void shouldLog() {
        given(req.getRequestURI()).willReturn("");
        callbackInterceptor.shouldLog(req);
    }

    @Test
    public void isIncludePayload() {
        callbackInterceptor.isIncludePayload();
    }

    @Test
    public void beforeRequest() {
        callbackInterceptor.beforeRequest(req, "");
    }

    @Test
    public void afterRequest() {
        callbackInterceptor.afterRequest(req, "");
    }
}
