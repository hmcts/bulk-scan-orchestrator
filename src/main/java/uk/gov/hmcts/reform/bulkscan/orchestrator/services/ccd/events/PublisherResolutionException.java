package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.events;


public class PublisherResolutionException extends RuntimeException {
    public PublisherResolutionException(String message) {
        super(message);
    }
}
