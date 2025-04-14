package uk.gov.hmcts.reform.bulkscan.orchestrator.client.internal;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.FeignConfiguration;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.CreatePaymentDTO;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.UpdatePaymentDTO;

@FeignClient(
    name = "paymentProcessorClient",
    url = "${payment-processor.url}",
    configuration = FeignConfiguration.class
)
public interface PaymentProcessorClient {

    @PostMapping("/create")
    void createPayment(@RequestBody CreatePaymentDTO createPaymentDTO);

    @PostMapping("/update")
    void updatePayment(@RequestBody UpdatePaymentDTO updatePaymentDTO);
}
