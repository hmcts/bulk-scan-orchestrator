package uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.payments;

import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.payments.model.PaymentsData;

public interface IPaymentsPublisher {

    void publishPayments(PaymentsData paymentsData);
}
