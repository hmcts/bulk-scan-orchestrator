package uk.gov.hmcts.reform.bulkscan.orchestrator.controllers

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
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
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.util.SocketUtils
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CaseRetriever
import java.io.File
import java.util.concurrent.TimeUnit

@ExtendWith(SpringExtension::class)
@SpringBootTest(
    classes = [
        IntegrationTestConfig::class,
        WireMockConfiguration::class
    ],
    webEnvironment = RANDOM_PORT
)
@TestPropertySource(properties = [
    "core_case_data.api.url=http://localhost:\${wiremock.port}",
    "idam.s2s-auth.url=http://localhost:\${wiremock.port}",
    "idam.api.url=http://localhost:\${wiremock.port}",
    "idam.users.sscs.username=bulkscanorchestrator+systemupdate@gmail.com",
    "idam.users.sscs.password=Password12",
    "queue.read-interval=100"
])
@AutoConfigureWireMock
class CaseRetrievalTest {
    companion object {
        init {
            //This needs to be done since AutoConfigureWireMock seems to have a bug where its using a random port.
            System.setProperty("wiremock.port", SocketUtils.findAvailableTcpPort().toString())
        }

        val USER_ID = "32"
        val JURIDICTION = "SSCS"
        val CASE_TYPE = CaseRetriever.CASE_TYPE_ID
        val CASE_REF = "1537879748168579"

        private fun retrieveCase() =
            "/caseworkers/${USER_ID}/jurisdictions/${JURIDICTION}/case-types/${CASE_TYPE}/cases/${CASE_REF}"
    }

    private val mockMessage = Message(File("src/test/resources/envelopes/example.json").readText())

    @Autowired
    private lateinit var server: WireMockServer

    @Autowired
    private lateinit var mockReceiver: IMessageReceiver

    @BeforeEach
    fun before() {
        `when`(mockReceiver.receive()).thenReturn(mockMessage, null)
//        server.startRecording(urlToRecord);
    }

    @Test
    fun `Should call to retrieve the case from ccd`() {
        await()
            .atMost(5, TimeUnit.SECONDS)
            .ignoreExceptions()
            .until {
                server.verify(getRequestedFor(urlEqualTo(retrieveCase())))
                true
            }
    }
}
