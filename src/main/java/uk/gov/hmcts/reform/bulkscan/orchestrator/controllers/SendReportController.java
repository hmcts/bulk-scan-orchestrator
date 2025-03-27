package uk.gov.hmcts.reform.bulkscan.orchestrator.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.payment.SendPaymentReportService;

import java.time.LocalDate;

import static org.springframework.format.annotation.DateTimeFormat.ISO.DATE;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.util.Util.validateAuthorization;

@RestController
@RequestMapping(path = "/reports")
public class SendReportController {

    private final String bulkScanApiKey;
    private final SendPaymentReportService sendPaymentReportService;

    @Autowired
    public SendReportController(
        @Value("${actions.api-key}") String bulkScanApiKey,
        SendPaymentReportService sendPaymentReportService) {
        this.bulkScanApiKey = bulkScanApiKey;
        this.sendPaymentReportService = sendPaymentReportService;
    }

    @PostMapping(path = "/email-daily-report")
    public void generateAndEmailReports(
        @RequestHeader(value = AUTHORIZATION, required = false) String authHeader) {
        validateAuthorization(authHeader, bulkScanApiKey);

        // email report
        sendPaymentReportService.send();
    }

    @PostMapping(path = "/email-report")
    public void generateAndEmailReports(
        @RequestHeader(value = AUTHORIZATION, required = false) String authHeader,
        @RequestParam(name = "date") @DateTimeFormat(iso = DATE) LocalDate date) {
        validateAuthorization(authHeader, bulkScanApiKey);

        // email report
        sendPaymentReportService.send(date);
    }

}
