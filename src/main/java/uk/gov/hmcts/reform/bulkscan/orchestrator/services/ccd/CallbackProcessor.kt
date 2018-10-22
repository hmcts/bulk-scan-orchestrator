package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd

import mu.KotlinLogging
import org.springframework.stereotype.Service
import uk.gov.hmcts.reform.ccd.client.model.CallbackRequest
import uk.gov.hmcts.reform.ccd.client.model.CallbackTypes
import uk.gov.hmcts.reform.ccd.client.model.CallbackTypes.ABOUT_TO_SUBMIT

private val logger = KotlinLogging.logger {}

@Service
class CallbackProcessor {
    /**
     * returns
     *  - a list of errors
     *  - *or* an empty list  if it successfully attaches the supplementary evidence.
     */
    fun processEventType(type: String, callback: CallbackRequest): List<String> {
        logger.info { "Processing ccd callback, type: $type, eventId: ${callback.eventId}" }
        return when (type) {
            ABOUT_TO_SUBMIT -> aboutToSubmit(callback)
            else -> {
                logger.error { "Invalid callback, type: $type, eventId: ${callback.eventId}" }
                listOf("Internal Error: invalid event: $type")
            }
        }
    }

    private fun aboutToSubmit(callback: CallbackRequest): List<String> = listOf()
}
