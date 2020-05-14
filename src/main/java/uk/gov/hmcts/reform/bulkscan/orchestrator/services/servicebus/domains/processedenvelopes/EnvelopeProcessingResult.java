package uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.processedenvelopes;

public class EnvelopeProcessingResult {

    public final Long ccdId;
    public final EnvelopeCcdAction envelopeCcdAction;

    public EnvelopeProcessingResult(Long ccdId, EnvelopeCcdAction envelopeCcdAction) {
        this.ccdId = ccdId;
        this.envelopeCcdAction = envelopeCcdAction;
    }
}
