package uk.gov.hmcts.reform.bulkscan.orchestrator.util;

import com.microsoft.azure.servicebus.IMessageHandler;
import com.microsoft.azure.servicebus.Message;

public class MessageSender {

    private final IMessageHandler processor;

    public MessageSender(IMessageHandler processor) {
        this.processor = processor;
    }

    public void send(Message message) {
        processor.onMessageAsync(message).join();
    }
}
