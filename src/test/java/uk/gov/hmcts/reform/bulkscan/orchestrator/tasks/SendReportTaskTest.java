package uk.gov.hmcts.reform.bulkscan.orchestrator.tasks;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.payment.SendPaymentReportService;
import uk.gov.hmcts.reform.bulkscan.orchestrator.tasks.payment.SendReportTask;

@ExtendWith({SpringExtension.class, MockitoExtension.class})
@ContextConfiguration(classes = SendReportTask.class)
public class SendReportTaskTest {

    @MockBean
    private SendPaymentReportService sendPaymentReportService;

    private SendReportTask sendReportTask;

    @BeforeEach
    void setUp() {
        sendReportTask = new SendReportTask(sendPaymentReportService);
    }

    @Test
    public void should_send_report() {
        sendReportTask.send();

        Mockito.verify(sendPaymentReportService, (Mockito.times(1))).send();
    }
}
