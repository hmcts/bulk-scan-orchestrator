package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd

import feign.FeignException
import mu.KotlinLogging
import org.springframework.stereotype.Service
import uk.gov.hmcts.reform.ccd.client.CoreCaseDataApi
import uk.gov.hmcts.reform.ccd.client.model.CallbackRequest
import uk.gov.hmcts.reform.ccd.client.model.CallbackTypes.ABOUT_TO_SUBMIT
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails

private val logger = KotlinLogging.logger {}

@Service
class CallbackProcessor(
    private val authenticatorFactory: CcdAuthenticatorFactory,
    private val ccdApi: CoreCaseDataApi
) {

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

    private fun aboutToSubmit(callback: CallbackRequest): List<String> {
        return when (callback.eventId) {
            "attachToExistingCase" -> attachToExistingCase(callback)
            else -> {
                logger.error { "Invalid event ID:${callback.eventId}" }
                listOf("Internal Error: Invalid event ID:${callback.eventId}")
            }
        }
    }

    private fun attachToExistingCase(callback: CallbackRequest): List<String> {
        return if (callback.caseDetails != null) {
            handleAttachRecord(callback.caseDetails)
        } else {
            logger.error { "No case details supplied eventId: ${callback.eventId}" }
            listOf("Internal Error: No case details supplied")
        }
    }

    private fun handleAttachRecord(caseDetails: CaseDetails): List<String> {
        return if (caseDetails.data != null) {
            startAttachEvent(caseDetails)
        } else {
            logger.error { "No case details supplied eventId: ${caseDetails.id}" }
            listOf("Internal Error: no case data")
        }
    }

    private fun startAttachEvent(caseDetails: CaseDetails): List<String> {
        try {
            val authenticator = authenticatorFactory.createForJurisdiction(caseDetails.jurisdiction)
            ccdApi.startEventForCaseWorker(
                authenticator.userToken,
                authenticator.serviceToken,
                authenticator.userDetails.id,
                caseDetails.jurisdiction,
                caseDetails.caseTypeId,
                caseDetails.data["attachToCaseReference"] as String?,
                "TBD"
            )
            return listOf()
        } catch (e: FeignException) {
            logger.error(e) {}
            return listOf("Internal Error: response ${e.status()} submitting event")
        } catch (e: Exception) {
            logger.error(e) {}
            return listOf("Internal Error: ${e::class.simpleName}:${e.message}")
        }
    }
}
