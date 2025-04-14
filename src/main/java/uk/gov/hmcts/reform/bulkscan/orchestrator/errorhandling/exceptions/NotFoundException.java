package uk.gov.hmcts.reform.bulkscan.orchestrator.errorhandling.exceptions;

/**
 * Custom exception which handles not found errors.
 */
public class NotFoundException extends RuntimeException {

    /**
     * Constructor for custom NotFoundException.
     *
     * @param message error message.
     */
    public NotFoundException(String message) {
        super(message);
    }
}
