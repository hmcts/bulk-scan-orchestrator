package uk.gov.hmcts.reform.bulkscan.orchestrator.logging;

import feign.FeignException;
import org.slf4j.Logger;

public final class FeignExceptionLogger {

    private FeignExceptionLogger() {
        // empty utility class construct
    }

    public static void debugCcdException(Logger logger, FeignException exception, String introMessage) {
        logger.debug(
            "{}. CCD response: {}",
            introMessage,
            exception.responseBody().map(b -> new String(b.array())).orElseGet(exception::getMessage)
        );
    }
}
