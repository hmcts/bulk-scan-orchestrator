package uk.gov.hmcts.reform.bulkscan.orchestrator.controllers

import org.springframework.util.SocketUtils
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CaseRetriever

object Environment{

    val USER_ID = "640"
    val JURIDICTION = "BULKSCAN"
    val CASE_TYPE = CaseRetriever.CASE_TYPE_ID
    val CASE_REF = "1539007368674134"

    val caseUrl = "/caseworkers/${USER_ID}/jurisdictions/${JURIDICTION}/case-types/${CASE_TYPE}/cases/${CASE_REF}"
    val caseEventUrl = "${caseUrl}/events"
    val caseTypeUrl = "/caseworkers/${USER_ID}/jurisdictions/${JURIDICTION}/case-types/${CASE_TYPE}"
    val caseSubmitUrl = "$caseTypeUrl/cases"
    val caseEventTriggerStartUrl = "$caseTypeUrl/event-triggers/createException/token"

    fun retrieveCase() =
        "/caseworkers/${USER_ID}/jurisdictions/${JURIDICTION}/case-types/${CASE_TYPE}/cases/${CASE_REF}"
}
