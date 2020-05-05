package uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.processedenvelopes;

public class EnvelopeProcessResult {

    public final Long ccdId;
    public final EnvelopeCcdAction processedCcdType;

    public EnvelopeProcessResult(Long ccdId, EnvelopeCcdAction processedCcdType) {
        this.ccdId = ccdId;
        this.processedCcdType = processedCcdType;
    }
}
