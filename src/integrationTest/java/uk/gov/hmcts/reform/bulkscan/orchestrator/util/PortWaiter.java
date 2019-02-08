package uk.gov.hmcts.reform.bulkscan.orchestrator.util;

import java.io.Closeable;
import java.io.IOException;
import java.net.Socket;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;

public final class PortWaiter {

    public static void waitFor(int port, long timeoutSeconds) {
        await()
            .atMost(timeoutSeconds, SECONDS)
            .ignoreExceptions()
            .until(() -> {
                try (Closeable socket = new Socket("localhost", port)) {
                    return true;
                } catch (IOException exception) {
                    return false;
                }
            });
    }

    public static void waitFor(int port) {
        waitFor(port, 5L);
    }

    private PortWaiter() {
        // utility class constructor
    }
}
