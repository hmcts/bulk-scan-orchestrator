package uk.gov.hmcts.reform.bulkscan.orchestrator.client.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.payment.Payment;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.payment.UpdatePayment;

import static org.springframework.http.MediaType.APPLICATION_JSON;

/**
 * Client for calling the Bulk Scan Payment Processor (BSPP) API endpoints.
 * The environment variable for base URL of BSPP needs to be set before using this client.
 * @see <a href="https://github.com/hmcts/bulk-scan-payment-processor">Repo for BSPP</a>
 */
@Component
public class PaymentApiClient {

    private static final Logger log = LoggerFactory.getLogger(PaymentApiClient.class);

    private final RestTemplate restTemplate;
    private final String paymentUrl;

    public PaymentApiClient(RestTemplate restTemplate, @Value("${payment.api.url}") final String paymentUrl) {
        this.restTemplate = restTemplate;
        this.paymentUrl = paymentUrl;

    }

    public ResponseEntity<String> postPayment(Payment payment) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Type", APPLICATION_JSON.toString());

        log.info("Posting payment to payment processor api for envelope. {}", payment.getEnvelopeId());

        return restTemplate.postForEntity(paymentUrl + "/create", new HttpEntity<>(payment, headers), String.class);

    }

    public ResponseEntity<String> postUpdatePayment(UpdatePayment updatePayment) {

        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Type", APPLICATION_JSON.toString());

        log.info("Posting update payment to payment processor api for envelope. {}", updatePayment.getEnvelopeId());

        return restTemplate.postForEntity(paymentUrl + "/update",
            new HttpEntity<>(updatePayment, headers), String.class);

    }


}
