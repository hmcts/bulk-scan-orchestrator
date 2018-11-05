package uk.gov.hmcts.reform.bulkscan.orchestrator

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.SpringApplication
import org.springframework.boot.WebApplicationType
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.test.context.ActiveProfiles
import uk.gov.hmcts.reform.bulkscan.orchestrator.dm.DocumentManagementUploadService
import java.io.File
import java.nio.file.Files

private val LOG = LoggerFactory.getLogger(UploadDocument::class.java)

@SpringBootApplication
@ActiveProfiles("nosb")
class UploadDocument(
    @Autowired val service: DocumentManagementUploadService,
    @Autowired val mapper: ObjectMapper

) : CommandLineRunner {

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            with(SpringApplication(UploadDocument::class.java)) {
                webApplicationType = WebApplicationType.NONE
                run(*args)
            }
        }
    }

    override fun run(vararg args: String) {
        try {
            LOG.info("Attempting to upload file")
            val file = File(args[0])
            if (file.isFile
                && (Files.exists(file.toPath()))
                || Files.exists(file.toPath())
            ) {
                System.out.println("Trying to upload ${file.name}")
                val result = service.uploadToDmStore(file.name, file.absolutePath)
                System.out.println("File link: ${result.embedded.documents[0].links.self.href}")
                //needed since the autoconfigured sb startup if the azure.servicebus.queue-name is set
                System.exit(0)
            } else {
                System.err.println("File ${file.name} does not valid file or does not exist please supply valid file.")
                System.exit(1)
            }
        } catch (e: Exception) {
            System.err.println("${e.message}")
            System.exit(1)
        }
    }
}
