package uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.processedenvelopes;

public class EnvelopeProcessResult {

    public final Long ccdId;
    public final ProcessedCcdType processedCcdType;

    public EnvelopeProcessResult(Long ccdId, ProcessedCcdType processedCcdType) {
        this.ccdId = ccdId;
        this.processedCcdType = processedCcdType;
    }
}
