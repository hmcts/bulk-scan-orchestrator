package uk.gov.hmcts.reform.bulkscan.orchestrator.services.payment;

import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import uk.gov.hmcts.reform.bulkscan.orchestrator.util.SendReportException;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertThrows;

@ExtendWith({SpringExtension.class, MockitoExtension.class})
@ContextConfiguration(classes = SendPaymentReportService.class)
public class SendPaymentReportServiceTest {

    @MockBean
    private PaymentService paymentService;

    @MockBean
    private UpdatePaymentService updatePaymentService;

    @MockBean
    private JavaMailSender javaMailSender;

    private SendPaymentReportService sendPaymentReportService;

    @BeforeEach
    void setUp() {
        sendPaymentReportService =
            new SendPaymentReportService(paymentService, javaMailSender, updatePaymentService,
                "bulkscanteam", new String[]{"recipients"});
    }

    @Test
    public void should_send_daily_payment_report() {
        Mockito.when(javaMailSender.createMimeMessage()).thenReturn(Mockito.mock(MimeMessage.class));
        sendPaymentReportService.send();

        Mockito.verify(javaMailSender, Mockito.times(1)).createMimeMessage();
        Mockito.verify(javaMailSender, Mockito.times(1)).send(Mockito.any(MimeMessage.class));
        Mockito.verify(paymentService, Mockito.times(1)).getAllByPaymentsForPreviousDay();
        Mockito.verify(updatePaymentService, Mockito.times(1)).getAllByPaymentsForPreviousDay();
    }

    @Test
    public void should_send_payment_report_by_date() {
        Mockito.when(javaMailSender.createMimeMessage()).thenReturn(Mockito.mock(MimeMessage.class));
        sendPaymentReportService.send(LocalDate.of(2020, 1, 1));

        Mockito.verify(javaMailSender, Mockito.times(1)).createMimeMessage();
        Mockito.verify(javaMailSender, Mockito.times(1)).send(Mockito.any(MimeMessage.class));
        Mockito.verify(paymentService, Mockito.times(1)).getAllByPaymentsByDate(LocalDate.of(2020, 1, 1));
        Mockito.verify(updatePaymentService, Mockito.times(1)).getAllByPaymentsByDate(LocalDate.of(2020, 1, 1));
    }

    @Test
    public void should_throw_exception_when_sending_daily_payment_report_failed() {
        Mockito.when(javaMailSender.createMimeMessage()).thenThrow(
            new MailSendException("Mail server connection failed")
        );

        Exception exception = assertThrows(SendReportException.class, () -> sendPaymentReportService.send());
        System.out.println(exception.getMessage());
        assertThat(exception.getMessage()).contains("Mail server connection failed");
        assertThat(exception.getMessage()).contains("Report could not be sent");
    }

    @Test
    public void should_throw_exception_when_sending_payment_report_failed() {
        Mockito.when(javaMailSender.createMimeMessage()).thenThrow(
            new MailSendException("Mail server connection failed")
        );

        Exception exception = assertThrows(SendReportException.class, () ->
            sendPaymentReportService.send(LocalDate.of(2020, 1, 1)));
        System.out.println(exception.getMessage());
        assertThat(exception.getMessage()).contains("Mail server connection failed");
        assertThat(exception.getMessage()).contains("Report could not be sent");
    }
}
