package uk.gov.hmcts.reform.bulkscan.orchestrator.util;

public class SendReportException extends RuntimeException {

    public SendReportException(String message, Throwable cause) {
        super(message + ": " + cause.getMessage());
    }
}
