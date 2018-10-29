package uk.gov.hmcts.reform.bulkscan.orchestrator.helper

import com.microsoft.azure.servicebus.IQueueClient
import com.microsoft.azure.servicebus.Message
import com.microsoft.azure.servicebus.QueueClient
import com.microsoft.azure.servicebus.primitives.ServiceBusException
import org.json.JSONException
import org.json.JSONObject
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.stereotype.Service
import uk.gov.hmcts.reform.bulkscan.orchestrator.FunctionalQueueConfig
import uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData
import java.util.*

private val logger = LoggerFactory.getLogger(EnvelopeMessager::class.java)

@Service
@Import(FunctionalQueueConfig::class)
class EnvelopeMessager( @Autowired client: QueueClient) : IQueueClient by client{

    @Throws(JSONException::class, ServiceBusException::class, InterruptedException::class)
    fun sendMessageFromFile(jsonFileName: String, caseRef: String?, poBox: UUID?) {

        val updateCaseData = JSONObject(SampleData.fileContentAsString(jsonFileName))
        updateCaseData.put("case_ref", caseRef)

        if (poBox != null) {
            updateCaseData.put("po_box", poBox)
        }

        val message = Message()
        message.messageId = UUID.randomUUID().toString()
        message.body = updateCaseData.toString().toByteArray()

        logger.info("Sending message to queue for the Case ID {} for updating the case", caseRef)
        send(message)
    }

}
