package uk.gov.hmcts.reform.bulkscan.orchestrator.controllers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.AbstractRequestLoggingFilter;

import javax.servlet.http.HttpServletRequest;

/*
This interceptor stands for debugging purposes. We don't want to log request bodies of any kind.
 */
@Component
public class CallbackInterceptor extends AbstractRequestLoggingFilter {

    // using own logger as logback is configured to track traces from hmcts package
    // built-in logger for request filters will not work
    private static final Logger LOGGER = LoggerFactory.getLogger(CallbackInterceptor.class);

    @Override
    protected boolean shouldLog(HttpServletRequest request) {
        return request.getRequestURI().contains("ccd-data-store-api");
    }

    @Override
    protected boolean isIncludePayload() {
        return true;
    }

    @Override
    protected void beforeRequest(HttpServletRequest request, String message) {
        LOGGER.info("BEFORE REQUEST: {}", message);
    }

    @Override
    protected void afterRequest(HttpServletRequest request, String message) {
        LOGGER.info("AFTER REQUEST: {}", message);
    }
}
