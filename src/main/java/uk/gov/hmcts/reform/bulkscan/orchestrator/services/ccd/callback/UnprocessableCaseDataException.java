package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.callback;

/**
 * Represents a situation when the case data in CCD callback is unprocessable.
 */
public class UnprocessableCaseDataException extends RuntimeException {

    public UnprocessableCaseDataException(String message) {
        super(message);
    }
}
