package uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.processedenvelopes;

/**
 * Sends notifications about processed messages.
 */
public interface IProcessedEnvelopeNotifier {

    void notify(String envelopeId, Long ccdId, EnvelopeCcdAction envelopeCcdAction);
}
