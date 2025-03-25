package uk.gov.hmcts.reform.bulkscan.orchestrator.controllers;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.callback.UnprocessableCaseDataException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.util.InvalidApiKeyException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.util.SendReportException;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY;
import static org.springframework.http.ResponseEntity.status;

@ControllerAdvice
public class ResponseExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ResponseExceptionHandler.class);

    @ExceptionHandler(InvalidRequestException.class)
    protected ResponseEntity<ErrorResponse> handleInvalidRequestException(Exception exception) {
        log.error("Received invalid request", exception);
        return status(BAD_REQUEST).body(new ErrorResponse(exception.getMessage()));
    }

    @ExceptionHandler(UnprocessableCaseDataException.class)
    protected ResponseEntity<ErrorResponse> handleUnprocessableCaseDataException(Exception exception) {
        log.error("Received unprocessable callback request", exception);
        return status(UNPROCESSABLE_ENTITY).body(new ErrorResponse(exception.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    protected ResponseEntity<ErrorResponse> handleInternalException(Exception exception) {
        log.error("Unhandled exception. Returning 500 response to client", exception);

        return status(INTERNAL_SERVER_ERROR).body(new ErrorResponse(exception.getMessage()));
    }

    /**
     * Handles InvalidApiKeyException.
     *
     * @param exc InvalidApiKeyException
     * @return ResponseEntity
     */
    @ExceptionHandler(InvalidApiKeyException.class)
    protected ResponseEntity<Void> handleInvalidApiKeyException(InvalidApiKeyException exc) {
        log.warn(exc.getMessage(), exc);
        return status(UNAUTHORIZED).build();
    }

    /**
     * Handles SendReportException
     *
     * @param exc SendReportException
     * @return ResponseEntity
     */
    @ExceptionHandler(SendReportException.class)
    protected ResponseEntity<String> handleSendReportException(SendReportException exc) {
        log.warn(exc.getMessage(), exc);
        return ResponseEntity.status(INTERNAL_SERVER_ERROR).body(exc.getMessage());
    }

    public class ErrorResponse {

        @JsonProperty("message")
        public final String message;

        public ErrorResponse(String message) {
            this.message = message;
        }
    }
}
