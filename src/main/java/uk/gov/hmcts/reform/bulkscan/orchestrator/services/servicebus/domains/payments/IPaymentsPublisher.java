package uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.payments;

import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.payments.model.CreatePaymentsCommand;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.payments.model.UpdatePaymentsCommand;

public interface IPaymentsPublisher {

    void send(CreatePaymentsCommand createPaymentsCommand);

    void send(UpdatePaymentsCommand updatePaymentsCommand);
}
