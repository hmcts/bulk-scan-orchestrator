package uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus;

import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.PaymentsData;

public interface IPaymentsPublisher {

    void publish(PaymentsData paymentsData);
}
