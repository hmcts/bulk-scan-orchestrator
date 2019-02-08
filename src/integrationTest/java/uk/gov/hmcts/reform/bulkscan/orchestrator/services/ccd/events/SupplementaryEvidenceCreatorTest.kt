package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.events

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.microsoft.azure.servicebus.Message
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.junit.jupiter.SpringExtension
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.Environment.CASE_EVENT_URL
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.Environment.GET_CASE_URL
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.IntegrationTest
import uk.gov.hmcts.reform.bulkscan.orchestrator.util.MessageSender
import java.io.File
import java.util.concurrent.TimeUnit

@ExtendWith(SpringExtension::class)
@IntegrationTest
class SupplementaryEvidenceCreatorTest {

    private val mockMessage = Message(File(
        "src/integrationTest/resources/servicebus/message/supplementary-evidence-example.json"
    ).readText())
    private val mockResponse = File("src/integrationTest/resources/ccd/response/sample-case.json").readText()

    @Autowired
    private lateinit var server: WireMockServer

    @Autowired
    private lateinit var messageSender: MessageSender

    @BeforeEach
    fun before() {
        //We need to do this because of an issue with the way AutoConfigureWireMock works with profiles.
        WireMock(server.port()).register(get(GET_CASE_URL).willReturn(aResponse().withBody(mockResponse)))

        messageSender.send(mockMessage)
    }

    @Test
    fun `should call ccd to attach supplementary evidence for caseworker`() {
        await()
            .atMost(60, TimeUnit.SECONDS)
            .pollInterval(2, TimeUnit.SECONDS)
            .ignoreExceptions()
            .until {
                server.verify(postRequestedFor(urlPathEqualTo(CASE_EVENT_URL)))
                true
            }
    }
}
