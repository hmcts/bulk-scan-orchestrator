package uk.gov.hmcts.reform.bulkscan.orchestrator.controllers

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.givenThat
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.microsoft.azure.servicebus.Message
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.test.context.junit.jupiter.SpringExtension
import uk.gov.hmcts.reform.bulkscan.orchestrator.controllers.config.Environment
import uk.gov.hmcts.reform.bulkscan.orchestrator.controllers.config.Environment.CASE_TYPE_BULK_SCAN
import uk.gov.hmcts.reform.bulkscan.orchestrator.controllers.config.Environment.CASE_TYPE_EXCEPTION_RECORD
import uk.gov.hmcts.reform.bulkscan.orchestrator.controllers.config.Environment.getCaseUrl
import uk.gov.hmcts.reform.bulkscan.orchestrator.controllers.config.IntegrationTest
import java.io.File
import java.util.concurrent.TimeUnit

@ExtendWith(SpringExtension::class)
@IntegrationTest
class ExceptionRecordCreatorTest {

    private val mockSupplementaryMessage = Message(File(
        "src/integrationTest/resources/servicebus/message/supplementary-evidence-example.json"
    ).readText())
    private val mockExceptionMessage = Message(File(
        "src/integrationTest/resources/servicebus/message/exception-example.json"
    ).readText())

    private val caseEventTriggerStartUrl = Environment.caseEventTriggerStartUrl
        .replace(CASE_TYPE_BULK_SCAN, CASE_TYPE_EXCEPTION_RECORD)
    private val caseSubmitUrl = Environment.caseSubmitUrl
        .replace(CASE_TYPE_BULK_SCAN, CASE_TYPE_EXCEPTION_RECORD)

    @Autowired
    private lateinit var server: WireMockServer

    @Autowired
    private lateinit var messageSender: MessageSender

    @BeforeEach
    fun before() {
        WireMock.configureFor(server.port())
        givenThat(get(getCaseUrl).willReturn(aResponse().withStatus(HttpStatus.NOT_FOUND.value())))
        givenThat(get(caseEventTriggerStartUrl).willReturn(aResponse().withBody(
            "{\"case_details\":null,\"event_id\":\"eid\",\"token\":\"etoken\"}"
        )))
    }

    @Test
    fun `should create exception record for supplementary evidence when case record is not found`() {
        messageSender.send(mockSupplementaryMessage)
        await()
            .atMost(30, TimeUnit.SECONDS)
            .ignoreExceptions()
            .until {
                server.verify(getRequestedFor(urlPathEqualTo(getCaseUrl)))
                server.verify(postRequestedFor(urlPathEqualTo(caseSubmitUrl)))
                true
            }
    }

    @Test
    fun `should create exception record for new exception case type`() {
        messageSender.send(mockExceptionMessage)
        await()
            .atMost(30, TimeUnit.SECONDS)
            .ignoreExceptions()
            .until {
                server.verify(postRequestedFor(urlPathEqualTo(caseSubmitUrl)))
                true
            }
    }
}
