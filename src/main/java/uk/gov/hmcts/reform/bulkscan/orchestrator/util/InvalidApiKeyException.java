package uk.gov.hmcts.reform.bulkscan.orchestrator.util;

/**
 * Exception - to be thrown when an endpoint is used without the expected authorisation.
 */
public class InvalidApiKeyException extends RuntimeException{

    public InvalidApiKeyException(String message) {
        super(message);
    }
}
