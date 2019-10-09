package uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.payments;

import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.payments.model.CreatePaymentsCommand;

public interface IPaymentsPublisher {

    void send(CreatePaymentsCommand createPaymentsCommand);
}
