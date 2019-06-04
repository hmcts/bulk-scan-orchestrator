package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.events;

import org.springframework.beans.factory.annotation.Autowired;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CcdApi;

abstract class AbstractEventPublisher {

    @Autowired
    protected CcdApi ccdApi;

    AbstractEventPublisher() {
    }

}
