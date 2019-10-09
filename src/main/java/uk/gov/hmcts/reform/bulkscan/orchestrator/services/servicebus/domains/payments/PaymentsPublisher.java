package uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.payments;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.servicebus.IMessage;
import com.microsoft.azure.servicebus.Message;
import com.microsoft.azure.servicebus.QueueClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.payments.model.CreatePaymentsCommand;

import java.time.Instant;

import static org.springframework.http.MediaType.APPLICATION_JSON;

@Service
@Profile("!nosb") // do not register for the nosb (test) profile
public class PaymentsPublisher implements IPaymentsPublisher {

    private static final Logger LOG = LoggerFactory.getLogger(PaymentsPublisher.class);

    private final QueueClient queueClient;
    private final ObjectMapper objectMapper;

    public PaymentsPublisher(
        @Qualifier("payments") QueueClient queueClient,
        ObjectMapper objectMapper
    ) {
        this.queueClient = queueClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public void send(CreatePaymentsCommand cmd) {
        try {
            String messageBody = objectMapper.writeValueAsString(cmd);

            IMessage message = new Message(
                cmd.ccdReference,
                messageBody,
                APPLICATION_JSON.toString()
            );
            message.setLabel(Labels.CREATE);

            queueClient.scheduleMessage(message, Instant.now().plusSeconds(10));

            LOG.info("Sent message to payments queue. CCD Reference: {}", cmd.ccdReference);
        } catch (Exception ex) {
            throw new PaymentsPublishingException(
                String.format(
                    "An error occurred when trying to publish payments for CCD Ref %s", cmd.ccdReference
                ),
                ex
            );
        }
    }
}
