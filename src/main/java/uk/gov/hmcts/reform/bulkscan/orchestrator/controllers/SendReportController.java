package uk.gov.hmcts.reform.bulkscan.orchestrator.controllers;

import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.payment.SendPaymentReportService;

import static org.slf4j.LoggerFactory.getLogger;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.util.Util.validateAuthorization;

@RestController
@RequestMapping(path = "/reports")
public class SendReportController {

    private final String bulkScanApiKey;
    private final SendPaymentReportService sendPaymentReportService;
    private static final Logger logger = getLogger(SendReportController.class);

    public SendReportController(
        @Value("${actions.api-key}") String bulkScanApiKey,
        SendPaymentReportService sendPaymentReportService) {
        this.bulkScanApiKey = bulkScanApiKey;
        this.sendPaymentReportService = sendPaymentReportService;
    }

    @PostMapping(path = "/payment")
    public void generateAndEmailReports(
        @RequestHeader(value = AUTHORIZATION, required = false) String authHeader) {
        validateAuthorization(authHeader, bulkScanApiKey);

        // email report
        sendPaymentReportService.send();
    }

}
