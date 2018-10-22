package uk.gov.hmcts.reform.bulkscan.orchestrator.controllers

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.givenThat
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.microsoft.azure.servicebus.IMessageReceiver
import com.microsoft.azure.servicebus.Message
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.util.SocketUtils
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CaseRetriever
import java.io.File
import java.util.concurrent.TimeUnit

@ExtendWith(SpringExtension::class)
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("integration")
@AutoConfigureWireMock
class ExceptionRecordCreatorTest {
    companion object {
        init {
            //This needs to be done since AutoConfigureWireMock seems to have a bug where its using a random port.
            System.setProperty("wiremock.port", SocketUtils.findAvailableTcpPort().toString())
        }

        val USER_ID = "640"
        val JURIDICTION = "BULKSCAN"
        val CASE_TYPE = CaseRetriever.CASE_TYPE_ID
        val CASE_REF = "1539007368674134"

        private val caseTypeUrl = "/caseworkers/$USER_ID/jurisdictions/$JURIDICTION/case-types/$CASE_TYPE"
        private val caseUrl = "$caseTypeUrl/cases/$CASE_REF"
        private val caseEventTriggerStartUrl = "$caseTypeUrl/event-triggers/createException/token"
        private val caseSubmitUrl = "$caseTypeUrl/cases"
    }

    private val mockSupplementaryMessage = Message(File(
        "src/integrationTest/resources/servicebus/message/supplementary-evidence-example.json"
    ).readText())
    private val mockExceptionMessage = Message(File(
        "src/integrationTest/resources/servicebus/message/exception-example.json"
    ).readText())

    @Autowired
    private lateinit var server: WireMockServer

    @Autowired
    private lateinit var mockReceiver: IMessageReceiver

    @BeforeEach
    fun before() {
        givenThat(get(caseUrl).willReturn(aResponse().withStatus(HttpStatus.NOT_FOUND.value())))
        givenThat(get(caseEventTriggerStartUrl).willReturn(aResponse().withBody(
            "{\"case_details\":null,\"event_id\":\"eid\",\"token\":\"etoken\"}"
        )))
    }

    @Test
    @Disabled("TODO injecting RecieverFactory bean to test Azure Service bus queue on AAT")
    fun `should create exception record for supplementary evidence when case record is not found`() {
        `when`(mockReceiver.receive()).thenReturn(mockSupplementaryMessage, null)

        await()
            .atMost(30, TimeUnit.SECONDS)
            .ignoreExceptions()
            .until {
                server.verify(getRequestedFor(urlPathEqualTo(caseUrl)))
                server.verify(postRequestedFor(urlPathEqualTo(caseSubmitUrl)))
                true
            }
    }

    @Test
    @Disabled("TODO injecting RecieverFactory bean to test Azure Service bus queue on AAT")
    fun `should create exception record for new exception case type`() {
        `when`(mockReceiver.receive()).thenReturn(mockExceptionMessage, null)

        await()
            .atMost(30, TimeUnit.SECONDS)
            .ignoreExceptions()
            .until {
                server.verify(postRequestedFor(urlPathEqualTo(caseSubmitUrl)))
                true
            }
    }
}
