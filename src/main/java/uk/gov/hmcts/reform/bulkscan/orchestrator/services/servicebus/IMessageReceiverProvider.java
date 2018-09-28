package uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus;

import com.microsoft.azure.servicebus.IMessageReceiver;

public interface IMessageReceiverProvider {
    IMessageReceiver get();
}
