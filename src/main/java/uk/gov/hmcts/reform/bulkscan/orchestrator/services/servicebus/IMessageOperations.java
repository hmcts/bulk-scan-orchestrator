package uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus;

import com.microsoft.azure.servicebus.primitives.ServiceBusException;

import java.util.UUID;

/**
 * Provides all the functions related with message lifecycle
 * that are used when processing messages from the queue.
 */
public interface IMessageOperations {

    void complete(UUID lockToken) throws InterruptedException, ServiceBusException;

    void deadLetter(
        UUID lockToken,
        String reason,
        String description
    ) throws InterruptedException, ServiceBusException;
}
