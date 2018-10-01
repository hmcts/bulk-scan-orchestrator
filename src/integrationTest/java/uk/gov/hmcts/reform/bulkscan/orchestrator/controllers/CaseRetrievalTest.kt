package uk.gov.hmcts.reform.bulkscan.orchestrator.controllers

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.common.Slf4jNotifier
import com.github.tomakehurst.wiremock.core.Options
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.microsoft.azure.servicebus.IMessageReceiver
import com.microsoft.azure.servicebus.Message
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.cloud.contract.wiremock.WireMockConfigurationCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit.jupiter.SpringExtension
import uk.gov.hmcts.reform.bulkscan.orchestrator.Application
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.MessageReceiverFactory
import java.io.File
import java.util.concurrent.CountDownLatch

val latch = CountDownLatch(1)
val mockReciever: IMessageReceiver = Mockito.mock(com.microsoft.azure.servicebus.IMessageReceiver::class.java)

@Import(Application::class)
open class TestConfig {

    @Bean
    fun options(): Options = WireMockConfiguration.options()

    @Bean
    @Primary
    fun testProvider(): MessageReceiverFactory = MessageReceiverFactory { mockReciever }

    @Bean
    fun optionsCustomizer(): WireMockConfigurationCustomizer = WireMockConfigurationCustomizer {
        it.withRootDirectory("src/test/resources/mapping").notifier(Slf4jNotifier(true))
    }
}

@ExtendWith(SpringExtension::class)
@SpringBootTest(classes = [TestConfig::class], webEnvironment = RANDOM_PORT)
//@AutoConfigureWireMock(port = 0)
@TestPropertySource(properties = [
    "core_case_data.api.url=http://localhost:4452",
    "idam.s2s-auth.url=http://localhost:4552",
    "idam.api.url=http://localhost:8080",
    "idam.users.sscs.username=bulkscanorchestrator+systemupdate@gmail.com",
    "idam.users.sscs.password=Password12"
])
class CaseRetrievalTest {

    private val mapper = ObjectMapper()
    private val mockMessage = Message(File("src/test/resources/example1.json").readText())

//    @Autowired
//    private lateinit var server: WireMockServer;

    @BeforeEach
    fun before() {
        //idam s2s-auth
        `when`(mockReciever.receive()).thenReturn(mockMessage, null)
//
//        val fileSource = SingleRootFileSource("src/test/resources/")
//        fileSource.createIfNecessary()
//        val filesFileSource = fileSource.child("__files")
//        filesFileSource.createIfNecessary()
//        val mappingsFileSource = fileSource.child("mappings")
//        mappingsFileSource.createIfNecessary()
//
//        server.startRecording("http://localhost:4552");
    }

//    @AfterEach
//    fun after() {
//        val recordedMappings = server.stopRecording()
//        recordedMappings
//            .stubMappings
//            .asSequence()
//            .map { mapper.writeValueAsString(it) }
//            .forEachIndexed { index, it ->
//                File("src/test/resources/the_file_${index}.json")
//                    .printWriter()
//                    .use { out -> out.write(it) }
//            }
//    }

    @Test
    fun `an initial test to check the wiring`() {
        latch.await()

        Thread.sleep(1000)
    }
}
