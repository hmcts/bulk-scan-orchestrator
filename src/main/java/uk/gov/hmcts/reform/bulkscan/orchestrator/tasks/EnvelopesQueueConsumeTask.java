package uk.gov.hmcts.reform.bulkscan.orchestrator.tasks;

import com.azure.messaging.servicebus.ServiceBusProcessorClient;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(value = "scheduling.task.check-envelopes-queue.enabled", matchIfMissing = true)
@ConditionalOnExpression("!${jms.enabled}")
public class EnvelopesQueueConsumeTask {

    private static final Logger log = LoggerFactory.getLogger(EnvelopesQueueConsumeTask.class);

    private final ServiceBusProcessorClient serviceBusProcessorClient;

    public EnvelopesQueueConsumeTask(
        ServiceBusProcessorClient serviceBusProcessorClient
    ) {
        this.serviceBusProcessorClient = serviceBusProcessorClient;
    }

    @PostConstruct()
    public void startProcessor() {
        serviceBusProcessorClient.start();
    }

    @Scheduled(fixedDelayString = "${scheduling.task.check-envelopes-queue.fixedDelay}")
    public void checkServiceBusProcessorClient() {
        if (!serviceBusProcessorClient.isRunning()) {
            log.error("Envelopes queue consume listener is NOT running!!!");
        } else {
            log.info("Envelopes queue consume listener is working.");
        }
    }

}
