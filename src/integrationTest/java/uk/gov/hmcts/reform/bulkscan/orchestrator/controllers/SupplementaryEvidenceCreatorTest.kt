package uk.gov.hmcts.reform.bulkscan.orchestrator.controllers

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
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
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit.jupiter.SpringExtension
import uk.gov.hmcts.reform.bulkscan.orchestrator.controllers.Environment.caseEventUrl
import uk.gov.hmcts.reform.bulkscan.orchestrator.controllers.Environment.caseUrl
import java.io.File
import java.util.concurrent.TimeUnit

@ExtendWith(SpringExtension::class)
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("integration")
@AutoConfigureWireMock
@ContextConfiguration(initializers = [IntegrationTestConfig::class])
class SupplementaryEvidenceCreatorTest {

    private val mockMessage = Message(File(
        "src/integrationTest/resources/servicebus/message/supplementary-evidence-example.json"
    ).readText())
    private val mockResponse = File("src/integrationTest/resources/ccd/response/sample-case.json").readText()

    @Autowired
    private lateinit var server: WireMockServer

    @Autowired
    private lateinit var mockReceiver: IMessageReceiver

    @BeforeEach
    fun before() {
        //We need to do this because of an issue with the way AutoConfigureWireMock works with profiles.
        WireMock(server.port()).register(
            get(caseUrl).willReturn(aResponse().withBody(mockResponse))
        )
        `when`(mockReceiver.receive()).thenReturn(mockMessage, null)
    }

    @Test
    fun `should call ccd to attach supplementary evidence for caseworker`() {
        await()
            .atMost(30, TimeUnit.SECONDS)
            .ignoreExceptions()
            .until {
                server.verify(postRequestedFor(urlPathEqualTo(caseEventUrl)))
                true
            }
    }
}
