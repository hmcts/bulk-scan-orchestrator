package uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.payments;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.payments.model.PaymentCommand;

@Service
@Profile("nosb") // do not register for the nosb (local) profile, that would need to use AMQP
public class PaymentsPublisherNoop implements IPaymentsPublisher {

    private static final Logger LOG = LoggerFactory.getLogger(PaymentsPublisherNoop.class);

    public PaymentsPublisherNoop() {
    }

    @Override
    public void send(PaymentCommand cmd) {
        LOG.info("Not sending payments {}", cmd);
    }
}
