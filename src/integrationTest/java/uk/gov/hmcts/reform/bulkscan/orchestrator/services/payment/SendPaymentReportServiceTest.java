package uk.gov.hmcts.reform.bulkscan.orchestrator.services.payment;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;
import com.icegreen.greenmail.util.ServerSetupTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.jdbc.JdbcTestUtils;
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.IntegrationTest;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.payment.Payment;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.payment.PaymentData;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.payment.UpdatePayment;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

@Disabled
@IntegrationTest
public class SendPaymentReportServiceTest {

    @Autowired
    private JavaMailSender mailSender;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private UpdatePaymentService updatePaymentService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
    private static final String USERNAME = "bulk_scan_team";
    private static final String PASSWORD = "test_password";

    @BeforeEach
    public void setUp() {
        greenMail = new GreenMail(new ServerSetup(
            3026,
            null,
            ServerSetupTest.SMTP.getProtocol()
        )
    );
        greenMail.setUser(USERNAME, PASSWORD);
        greenMail.start();
        System.out.println("GreenMail started: " + greenMail.isRunning());
        greenMail.getSmtp();

        sendPaymentReportService = new SendPaymentReportService(
            paymentService, mailSender, updatePaymentService,
            "bulk_scan_team", new String[]{"payment_report_recipients"});

        paymentService.savePayment(payment);
        updatePaymentService.savePayment(updatePayment);
    }

    @AfterEach
    void tearDown() {
        JdbcTestUtils.deleteFromTables(
            jdbcTemplate, "payments_data", "payments", "update_payments");

        greenMail.stop();
        greenMail.reset();
    }

    @Test
    public void should_send_payment_report_by_email() {
        sendPaymentReportService.send(LocalDate.now());

        greenMail.waitForIncomingEmail(1);

        assertThat(greenMail.getReceivedMessages()).hasSize(1);
    }

}
