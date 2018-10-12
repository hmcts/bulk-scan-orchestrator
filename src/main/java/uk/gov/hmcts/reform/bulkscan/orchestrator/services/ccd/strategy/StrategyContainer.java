package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.strategy;

import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

/**
 * Container class to hold availableStrategies strategies enabled by this project.
 * In order to enable one must do:
 * <ul>
 *     <li>implement {@link AbstractStrategy}</li>
 *     <li>include {@code @Resource(name = "strategy-name") private Strategy someStrategy;}</li>
 *     <li>use resource in {@link this#getStrategy(Envelope, CaseDetails)}</li>
 * </ul>
 */
@Component
public class StrategyContainer {

    public Strategy getStrategy(Envelope envelope, CaseDetails caseDetails) {
        Strategy strategy = null;

        switch (envelope.classification) {
            case SUPPLEMENTARY_EVIDENCE:
            case EXCEPTION:
            case NEW_APPLICATION:
            default:
                break;
        }

        return strategy;
    }

    private StrategyContainer() {
        // utility class constructor
    }
}
