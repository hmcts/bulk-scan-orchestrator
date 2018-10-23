package uk.gov.hmcts.reform.bulkscan.orchestrator.controllers

import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.events.EventPublisher

/**
 * This Singleton contains all the environmental items needed in the integration test profile.
 */
object Environment {

    val USER_ID = "640"
    val JURIDICTION = "BULKSCAN"
    val CASE_TYPE_BULK_SCAN = EventPublisher.BULK_SCANNED
    val CASE_TYPE_EXCEPTION_RECORD = EventPublisher.EXCEPTION_RECORD
    val CASE_REF = "1539007368674134"

    val caseUrl = "/caseworkers/${USER_ID}/jurisdictions/${JURIDICTION}/case-types/${CASE_TYPE_BULK_SCAN}/cases/${CASE_REF}"
    val caseEventUrl = "${caseUrl}/events"
    val caseTypeUrl = "/caseworkers/${USER_ID}/jurisdictions/${JURIDICTION}/case-types/${CASE_TYPE_BULK_SCAN}"
    val caseSubmitUrl = "$caseTypeUrl/cases"
    val caseEventTriggerStartUrl = "$caseTypeUrl/event-triggers/createException/token"

    /** url for retrieve Case */
    fun retrieveCase() =
        "/caseworkers/${USER_ID}/jurisdictions/${JURIDICTION}/case-types/${CASE_TYPE_BULK_SCAN}/cases/${CASE_REF}"
}
