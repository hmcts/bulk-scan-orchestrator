package uk.gov.hmcts.reform.bulkscan.orchestrator.services.payment;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetup;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.jdbc.JdbcTestUtils;
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.IntegrationTest;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.payment.Payment;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.payment.PaymentData;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.payment.UpdatePayment;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.MULTIPART_MIXED_VALUE;

@IntegrationTest
public class SendPaymentReportServiceTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private UpdatePaymentService updatePaymentService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final String RECIPIENT_1 = "payment_report_recipients";
    private static final String SUBJECT = "Bulk Scan Orchestrator payments daily report";
    public static final String EMAIL_BODY = "This is an auto generated email. Do not respond to it.";

    final UpdatePayment updatePayment = new UpdatePayment(
        Instant.now(),
        "exceptionRecordRef",
        "newCaseRef",
        "envelope_id",
        "jurisdiction",
        "awaiting"
    );

    final PaymentData paymentData = new PaymentData("123");
    final Payment payment = new Payment(
        "envelope_id",
        Instant.now(),
        "ccdReference",
        "jurisdiction",
        "service",
        "poBox",
        true,
        "awaiting",
        Collections.singletonList(paymentData)
    );

    SendPaymentReportService sendPaymentReportService;

    private GreenMail greenMail;

    // as defined in application.properties
    private static final String USERNAME = "bulkscan_team";
    private static final String PASSWORD = "test_password";

    @BeforeEach
    public void setUp() {
        greenMail = new GreenMail(new ServerSetup(
            3025,
            null,
            ServerSetupTest.SMTP.getProtocol()
        )
        );
        greenMail.setUser(USERNAME, PASSWORD);
        greenMail.start();
        greenMail.getSmtp();

        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost("localhost");
        mailSender.setPort(3025);

        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "false");
        props.put("mail.smtp.starttls.enable", "false");
        props.put("mail.debug", "true");

        sendPaymentReportService = new SendPaymentReportService(
            paymentService, mailSender, updatePaymentService,
            "bulkscan_team", new String[]{"payment_report_recipients"});

        paymentService.savePayment(payment);
        updatePaymentService.savePayment(updatePayment);
    }

    @AfterEach
    void tearDown() {
        JdbcTestUtils.deleteFromTables(
            jdbcTemplate, "payments_data", "payments", "update_payments");

        greenMail.stop();
    }

    @Bean
    public JavaMailSender getMailSender() {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost("localhost");
        mailSender.setPort(ServerSetup.SMTP.getPort());

        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "false");
        props.put("mail.smtp.starttls.enable", "false");
        props.put("mail.debug", "true");

        return mailSender;
    }

    @Test
    public void should_send_payment_report_by_email() throws MessagingException, IOException {
        sendPaymentReportService.send(LocalDate.now());

        greenMail.waitForIncomingEmail(1);

        assertThat(greenMail.getReceivedMessages()).hasSize(1);
        MimeMessage msg = greenMail.getReceivedMessages()[0];
        String headers = GreenMailUtil.getHeaders(msg);
        assertThat(headers).contains("From: " + USERNAME);
        assertThat(headers).contains("To: " + RECIPIENT_1);
        assertThat(headers).contains("Subject: " + SUBJECT);
        assertThat(msg.getContentType()).startsWith(MULTIPART_MIXED_VALUE);

        assertThat(GreenMailUtil.getBody(msg)).contains(EMAIL_BODY);
    }

    @Test
    public void should_send_daily_payment_report_by_email() throws MessagingException {
        sendPaymentReportService.send();

        greenMail.waitForIncomingEmail(1);

        assertThat(greenMail.getReceivedMessages()).hasSize(1);
        MimeMessage msg = greenMail.getReceivedMessages()[0];
        String headers = GreenMailUtil.getHeaders(msg);
        assertThat(headers).contains("From: " + USERNAME);
        assertThat(headers).contains("To: " + RECIPIENT_1);
        assertThat(headers).contains("Subject: " + SUBJECT);
        assertThat(msg.getContentType()).startsWith(MULTIPART_MIXED_VALUE);

        assertThat(GreenMailUtil.getBody(msg)).contains(EMAIL_BODY);
    }

}
