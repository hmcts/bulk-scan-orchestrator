package uk.gov.hmcts.reform.bulkscan.orchestrator.controllers.config

/**
 * This Singleton contains all the environmental items needed in the integration test profile.
 */
object Environment {

    val USER_ID = "640"
    val JURIDICTION = "BULKSCAN"
    const val CASE_TYPE_BULK_SCAN = "Bulk_Scanned"
    const val CASE_TYPE_EXCEPTION_RECORD = "ExceptionRecord"
    val CASE_REF = "1539007368674134"

    private val caseTypeUrl = "/caseworkers/$USER_ID/jurisdictions/$JURIDICTION/case-types/$CASE_TYPE_BULK_SCAN"
    val caseUrl = "$caseTypeUrl/cases/$CASE_REF"
    val caseEventUrl = "$caseUrl/events"
    val caseSubmitUrl = "$caseTypeUrl/cases"
    val caseEventTriggerStartUrl = "$caseTypeUrl/event-triggers/createException/token"
}
