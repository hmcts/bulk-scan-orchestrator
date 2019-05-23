package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

public class MultipleCasesFoundException extends RuntimeException {

    public MultipleCasesFoundException(String message) {
        super(message);
    }
}
