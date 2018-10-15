package uk.gov.hmcts.reform.bulkscan.orchestrator.controllers

import com.github.tomakehurst.wiremock.WireMockServer
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
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.util.SocketUtils
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CaseRetriever
import java.io.File
import java.util.concurrent.TimeUnit

@ExtendWith(SpringExtension::class)
@SpringBootTest(
    classes = [IntegrationTestConfig::class],
    webEnvironment = RANDOM_PORT
)
@TestPropertySource(properties = [
    "core_case_data.api.url=${TestUrls.wiremockUrls.CORE_CASE_DATA_URL}",
    "idam.s2s-auth.url=${TestUrls.wiremockUrls.IDAM_S2S_URL}",
    "idam.api.url=${TestUrls.wiremockUrls.IDAM_API_URL}",
    "idam.users.bulkscan.username=bulkscan+ccd@gmail.com",
    "idam.users.bulkscan.password=Password12",
    "queue.read-interval=100"
])
@AutoConfigureWireMock
class SupplementaryEvidenceCreatorTest {
    companion object {
        init {
            //This needs to be done since AutoConfigureWireMock seems to have a bug where its using a random port.
            System.setProperty("wiremock.port", SocketUtils.findAvailableTcpPort().toString())
        }

        val USER_ID = "640"
        val JURIDICTION = "BULKSCAN"
        val CASE_TYPE = CaseRetriever.CASE_TYPE_ID
        val CASE_REF = "1539007368674134"

        private fun submitCaseEventUrl() =
            "/caseworkers/${USER_ID}/jurisdictions/${JURIDICTION}/case-types/${CASE_TYPE}/cases/${CASE_REF}/events"
    }

    private val mockMessage = Message(File("src/test/resources/example1.json").readText())

    @Autowired
    private lateinit var server: WireMockServer

    @Autowired
    private lateinit var mockReceiver: IMessageReceiver

    @BeforeEach
    fun before() {
        `when`(mockReceiver.receive()).thenReturn(mockMessage, null)
    }

    @Test
    fun `should call ccd to attach supplementary evidence for caseworker`() {
        await()
            .atMost(30, TimeUnit.SECONDS)
            .ignoreExceptions()
            .until {
                server.verify(postRequestedFor(urlPathEqualTo(submitCaseEventUrl())))
                true
            }
    }
}
