package uk.gov.hmcts.reform.bulkscan.orchestrator.util

import com.microsoft.azure.servicebus.IMessageHandler
import com.microsoft.azure.servicebus.Message
import org.springframework.beans.factory.annotation.Autowired

class MessageSender(@Autowired private val processor: IMessageHandler) {
    fun send(message: Message) {
        processor.onMessageAsync(message).get()
    }
}
