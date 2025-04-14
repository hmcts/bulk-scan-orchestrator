package uk.gov.hmcts.reform.bulkscan.orchestrator.errorhandling;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import uk.gov.hmcts.reform.bulkscan.orchestrator.errorhandling.exceptions.InvalidRequestException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.errorhandling.exceptions.NotFoundException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.errorhandling.exceptions.PaymentReprocessFailedException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.errorhandling.exceptions.UnprocessableCaseDataException;

import java.time.LocalDateTime;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY;

/**
 * Global exception handler, that captures exceptions thrown and returns a standard response to the user.
 */
@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ExceptionResponse> handle(NotFoundException ex) {
        log.error(ex.getMessage(), ex);

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(generateExceptionResponse(ex.getMessage()));
    }

    @ExceptionHandler(InvalidRequestException.class)
    protected ResponseEntity<ExceptionResponse> handle(InvalidRequestException ex) {
        log.error(ex.getMessage(), ex);

        return ResponseEntity.status(BAD_REQUEST)
            .body(generateExceptionResponse(ex.getMessage()));
    }

    @ExceptionHandler(UnprocessableCaseDataException.class)
    protected ResponseEntity<ExceptionResponse> handle(UnprocessableCaseDataException ex) {
        log.error(ex.getMessage(), ex);

        return ResponseEntity.status(UNPROCESSABLE_ENTITY)
            .body(generateExceptionResponse(ex.getMessage()));
    }

    @ExceptionHandler(PaymentReprocessFailedException.class)
    protected ResponseEntity<ExceptionResponse> handle(PaymentReprocessFailedException ex) {
        log.error(ex.getMessage(), ex);

        return ResponseEntity.status(UNPROCESSABLE_ENTITY).body(generateExceptionResponse(ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    protected ResponseEntity<ExceptionResponse> handle(Exception ex) {
        log.error(ex.getMessage(), ex);

        return ResponseEntity.status(INTERNAL_SERVER_ERROR)
            .body(generateExceptionResponse(ex.getMessage()));
    }

    private ExceptionResponse generateExceptionResponse(String message) {
        ExceptionResponse exceptionResponse = new ExceptionResponse();
        exceptionResponse.setMessage(message);
        exceptionResponse.setTimestamp(LocalDateTime.now());
        return exceptionResponse;
    }
}
