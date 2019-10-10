package uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.payments;

import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.payments.model.PaymentCommand;

public interface IPaymentsPublisher {

    void send(PaymentCommand cmd);
}
