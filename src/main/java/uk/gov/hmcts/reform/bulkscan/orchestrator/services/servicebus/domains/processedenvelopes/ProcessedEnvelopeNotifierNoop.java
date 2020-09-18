package uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.processedenvelopes;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.servicebus.IMessage;
import com.microsoft.azure.servicebus.Message;
import com.microsoft.azure.servicebus.QueueClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.Instant;

import static org.springframework.http.MediaType.APPLICATION_JSON;

/**
 * Notifies about successfully processed envelopes via queue.
 */
@Service
@Profile("nosb") // do not register for the nosb (local) profile, that would need to use AMQP (or not if we don't care)
public class ProcessedEnvelopeNotifierNoop implements IProcessedEnvelopeNotifier {

    private final Logger log = LoggerFactory.getLogger(ProcessedEnvelopeNotifierNoop.class);

    public ProcessedEnvelopeNotifierNoop() {
    }

    public void notify(String envelopeId, Long ccdId, EnvelopeCcdAction envelopeCcdAction) {
        log.info("Not notifying for envelope: {}", envelopeId);
    }
}
