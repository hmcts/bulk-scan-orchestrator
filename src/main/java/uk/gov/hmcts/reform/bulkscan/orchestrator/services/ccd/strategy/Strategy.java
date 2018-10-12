package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.strategy;

import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;

public interface Strategy {

    void execute(Envelope envelope);
}
