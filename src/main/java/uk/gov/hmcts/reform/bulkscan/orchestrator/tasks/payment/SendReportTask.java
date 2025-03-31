package uk.gov.hmcts.reform.bulkscan.orchestrator.tasks.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.payment.SendPaymentReportService;

@Component
@ConditionalOnProperty(prefix = "spring.mail", name = "host")
public class SendReportTask {

    private static final Logger log = LoggerFactory.getLogger(SendReportTask.class);
    private final SendPaymentReportService sendPaymentReportService;

    public SendReportTask(
        SendPaymentReportService sendPaymentReportService) {
        this.sendPaymentReportService = sendPaymentReportService;
    }

    @Scheduled(cron = "${payment.reports.cron}")
    public void send() {
        try {
            sendPaymentReportService.send();
            log.info("Successfully sent daily payment reports.");
        } catch (Exception exc) {
            log.error("Error sending daily payment reports", exc);
        }
    }
}
