package uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus;

/**
 * Sends notifications about processed messages.
 */
public interface IProcessedEnvelopeNotifier {

    void notify(String envelopeId);
}
