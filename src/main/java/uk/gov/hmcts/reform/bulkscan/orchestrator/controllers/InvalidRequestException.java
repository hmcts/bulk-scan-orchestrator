package uk.gov.hmcts.reform.bulkscan.orchestrator.controllers;

public class InvalidRequestException extends RuntimeException {
    public InvalidRequestException(String message) {
        super(message);
    }
}
