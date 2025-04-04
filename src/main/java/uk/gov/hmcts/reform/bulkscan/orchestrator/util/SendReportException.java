package uk.gov.hmcts.reform.bulkscan.orchestrator.util;

/**
 * Exception - Should be thrown when there are issues trying to create or email reports.
 */
public class SendReportException extends RuntimeException {

    public SendReportException(String message, Throwable cause) {
        super(message + ": " + cause.getMessage());
    }
}
