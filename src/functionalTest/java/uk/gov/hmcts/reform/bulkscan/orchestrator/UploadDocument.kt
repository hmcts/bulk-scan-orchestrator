package uk.gov.hmcts.reform.bulkscan.orchestrator

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
private val defaultFile = "src/functionalTest/resources/documents/supplementary-evidence.pdf"

@SpringBootApplication
@ActiveProfiles("nosb")
class UploadDocument(@Autowired val service: DocumentManagementUploadService) : CommandLineRunner {

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
            val filename = if (args.isNotEmpty() && args[0].isNotEmpty()) args[0] else defaultFile
            LOG.info("Attempting to upload file $filename")
            val file = File(filename)
            LOG.info("Trying to upload ${file.absoluteFile}")
            val result = service.uploadFile(file.name, file)
            println("File link: ${result}")
            //needed since the autoconfigured sb startup if the azure.servicebus.queue-name is set
            System.exit(0)
        } catch (e: Exception) {
            LOG.error("${e.message}", e)
            System.exit(1)
        }
    }
}
