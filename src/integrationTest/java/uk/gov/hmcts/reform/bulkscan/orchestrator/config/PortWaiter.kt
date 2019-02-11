package uk.gov.hmcts.reform.bulkscan.orchestrator.config

import org.awaitility.Awaitility.await
import java.net.Socket
import java.util.concurrent.TimeUnit.SECONDS

object PortWaiter {
    @JvmStatic
    @JvmOverloads
    fun waitFor(port: Int, timeoutSeconds: Long = 5L) {
        await()
            .atMost(timeoutSeconds, SECONDS)
            .ignoreExceptions()
            .until { Socket("localhost", port).use { true } }
    }
}
