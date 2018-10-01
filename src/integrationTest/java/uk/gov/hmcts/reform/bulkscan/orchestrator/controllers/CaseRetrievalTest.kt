package uk.gov.hmcts.reform.bulkscan.orchestrator.controllers

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.common.Slf4jNotifier
import com.github.tomakehurst.wiremock.core.Options
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.microsoft.azure.servicebus.IMessageReceiver
import com.microsoft.azure.servicebus.Message
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit.jupiter.SpringExtension
import uk.gov.hmcts.reform.bulkscan.orchestrator.Application
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CaseRetriever
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.MessageReceiverFactory
import java.io.File
import java.util.concurrent.TimeUnit

val mockReciever: IMessageReceiver = Mockito.mock(com.microsoft.azure.servicebus.IMessageReceiver::class.java)

@Import(Application::class)
open class TestConfig {
    @Bean
    fun options(): Options = WireMockConfiguration.options()
        //TODO we need to make this dynamic somehow. Maybe a RandomPropertySource ?
        .port(60222)
        //Change this to true if you need to debug
        .notifier(Slf4jNotifier(false))
    @Bean
    @Primary
    fun testProvider(): MessageReceiverFactory = MessageReceiverFactory { mockReciever }
}

private const val WIRE_MOCK_URL = "http://localhost:60222"

object WiremockUrls {
    const val IDAM_S2S_URL = WIRE_MOCK_URL
    const val IDAM_API_URL = WIRE_MOCK_URL
    const val CORE_CASE_DATA_URL = WIRE_MOCK_URL
}

//Use these to record or test against the docker versions.
object DockerUrls {
    const val IDAM_S2S_URL = "http://localhost:4552"
    const val IDAM_API_URL = "http://localhost:8080"
    const val CORE_CASE_DATA_URL = "http://localhost:4452"
}

@ExtendWith(SpringExtension::class)
@SpringBootTest(classes = [TestConfig::class], webEnvironment = RANDOM_PORT)
@AutoConfigureWireMock(port = 60222)
@TestPropertySource(properties = [
    "core_case_data.api.url=${WiremockUrls.CORE_CASE_DATA_URL}",
    "idam.s2s-auth.url=${WiremockUrls.IDAM_S2S_URL}",
    "idam.api.url=${WiremockUrls.IDAM_API_URL}",
    "idam.users.sscs.username=bulkscanorchestrator+systemupdate@gmail.com",
    "idam.users.sscs.password=Password12",
    "queue.read-interval=100"
])
class CaseRetrievalTest {
    companion object {
        val USER_ID = "32"
        val JURIDICTION = "SSCS"
        val CASE_TYPE = CaseRetriever.CASE_TYPE_ID
        val CASE_REF = "1537879748168579"
        private fun retrieveCase() =
            "/caseworkers/${USER_ID}/jurisdictions/${JURIDICTION}/case-types/${CASE_TYPE}/cases/${CASE_REF}"
    }

    private val mockMessage = Message(File("src/test/resources/example1.json").readText())

    @Autowired
    private lateinit var server: WireMockServer

    @BeforeEach
    fun before() {
        `when`(mockReciever.receive()).thenReturn(mockMessage, null)
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
