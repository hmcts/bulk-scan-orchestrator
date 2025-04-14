package uk.gov.hmcts.reform.bulkscan.orchestrator.errorhandling;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Class to structure exception response.
 */
@Data
public class ExceptionResponse {

    /**
     * The error message to return.
     */
    private String message;

    /**
     * The timestamp of when the error occurred.
     */
    private LocalDateTime timestamp;

}
