package uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.servicebus.IMessage;
import com.microsoft.azure.servicebus.Message;
import com.microsoft.azure.servicebus.QueueClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.PaymentsData;

import java.time.Instant;

import static org.springframework.http.MediaType.APPLICATION_JSON;

@Service
@Profile("!nosb") // do not register for the nosb (test) profile
public class PaymentsPublisher implements IPaymentsPublisher {

    private final Logger log = LoggerFactory.getLogger(PaymentsPublisher.class);

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
    public void publishPayments(PaymentsData paymentsData) {
        try {
            String messageBody = objectMapper.writeValueAsString(paymentsData);

            IMessage message = new Message(
                paymentsData.ccdReference,
                messageBody,
                APPLICATION_JSON.toString()
            );

            queueClient.scheduleMessage(message, Instant.now().plusSeconds(10));

            log.info("Sent message to payments queue. CCD Reference: {}", paymentsData.ccdReference);
        } catch (Exception ex) {
            throw new PaymentsPublishingException(
                String.format("An error occurred when trying to publish payments for CCD %s", paymentsData.ccdReference),
                ex
            );
        }
    }
}
