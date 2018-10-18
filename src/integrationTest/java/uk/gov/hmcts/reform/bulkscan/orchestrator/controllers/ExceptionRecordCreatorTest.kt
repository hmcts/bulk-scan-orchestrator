package uk.gov.hmcts.reform.bulkscan.orchestrator.controllers

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
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
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit.jupiter.SpringExtension
import uk.gov.hmcts.reform.bulkscan.orchestrator.controllers.Environment.caseEventTriggerStartUrl
import uk.gov.hmcts.reform.bulkscan.orchestrator.controllers.Environment.caseSubmitUrl
import uk.gov.hmcts.reform.bulkscan.orchestrator.controllers.Environment.caseUrl
import java.io.File
import java.util.concurrent.TimeUnit

@ExtendWith(SpringExtension::class)
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("integration")
@AutoConfigureWireMock
@ContextConfiguration(initializers = [IntegrationTestConfig::class])
class ExceptionRecordCreatorTest {

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
        WireMock.configureFor(server.port())
        givenThat(get(caseUrl).willReturn(aResponse().withStatus(HttpStatus.NOT_FOUND.value())))
        givenThat(get(caseEventTriggerStartUrl).willReturn(aResponse().withBody(
            "{\"case_details\":null,\"event_id\":\"eid\",\"token\":\"etoken\"}"
        )))
    }

    @Test
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
