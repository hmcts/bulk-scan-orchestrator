package uk.gov.hmcts.reform.bulkscan.orchestrator.services.payment;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.payment.Payment;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.payment.UpdatePayment;
import uk.gov.hmcts.reform.bulkscan.orchestrator.util.CsvWriter;
import uk.gov.hmcts.reform.bulkscan.orchestrator.util.SendReportException;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

@ConditionalOnProperty(prefix = "spring.mail", name = "host")
@Service
public class SendPaymentReportService {

    private static final Logger log = LoggerFactory.getLogger(SendPaymentReportService.class);

    private final JavaMailSender mailSender;
    private final PaymentService paymentService;
    private final UpdatePaymentService updatePaymentService;
    private final String from;
    private final String[] recipients;

    public static final String EMAIL_SUBJECT = "Bulk Scan Orchestrator payments daily report";
    public static final String EMAIL_BODY = "This is an auto generated email. Do not respond to it.";
    public static final String ATTACHMENT_PREFIX_1 = "Bulk-Scan-Orchestrator-Payment-Daily-Report-";
    public static final String ATTACHMENT_PREFIX_2 = "Bulk-Scan-Orchestrator-Update-Payment-Daily-Report-";

    public SendPaymentReportService(
        PaymentService paymentService,
        JavaMailSender mailSender,
        UpdatePaymentService updatePaymentService,
        @Value("${spring.mail.username}") String from,
        @Value("${payment.reports.recipients}") String[] recipients) {

        this.paymentService = paymentService;
        this.mailSender = mailSender;
        this.updatePaymentService = updatePaymentService;
        this.from = from;
        this.recipients = recipients;
    }

    public void send() {
        try {
            log.info("Sending daily report: {}", EMAIL_SUBJECT);
            MimeMessage msg = mailSender.createMimeMessage();

            MimeMessageHelper helper = new MimeMessageHelper(msg, true);
            helper.setFrom(from);
            helper.setTo(this.recipients);
            helper.setSubject(EMAIL_SUBJECT);
            helper.setText(EMAIL_BODY);
            helper.addAttachment(ATTACHMENT_PREFIX_1 + LocalDate.now() + ".csv", getCreatePaymentCsvReport());
            helper.addAttachment(ATTACHMENT_PREFIX_2 + LocalDate.now() + ".csv", getUpdatePaymentCsvReport());
            mailSender.send(msg);

        } catch (Exception exc) {
            log.error("Error sending report", exc);
            throw new SendReportException("Report could not be sent.", exc);
        }
    }

    private File getCreatePaymentCsvReport() throws IOException {
        List<Payment> allByPaymentsForPreviousDay = paymentService.getAllByPaymentsForPreviousDay();
        return CsvWriter.writeCreatePaymentsToCsv(allByPaymentsForPreviousDay);
    }

    private File getUpdatePaymentCsvReport() throws IOException {
        List<UpdatePayment> allByPaymentsForPreviousDay = updatePaymentService.getAllByPaymentsForPreviousDay();
        return CsvWriter.writeUpdatePaymentsToCsv(allByPaymentsForPreviousDay);
    }

}
