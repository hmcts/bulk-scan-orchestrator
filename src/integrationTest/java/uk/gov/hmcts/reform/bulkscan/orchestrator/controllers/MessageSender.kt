package uk.gov.hmcts.reform.bulkscan.orchestrator.controllers

import com.microsoft.azure.servicebus.IMessageHandler
import com.microsoft.azure.servicebus.Message
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired

class MessageSender(@Autowired private val processor: IMessageHandler) {
    val logger: Logger = LoggerFactory.getLogger(this::class.simpleName)
    fun send(message: Message) {
        processor.onMessageAsync(message).get()
    }
}
