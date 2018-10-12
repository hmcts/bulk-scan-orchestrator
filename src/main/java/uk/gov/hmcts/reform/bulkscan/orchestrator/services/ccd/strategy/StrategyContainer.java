package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.strategy;

import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import javax.annotation.Resource;

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

    @Resource(name = "attach-docs-to-supplementary-evidence")
    private Strategy attachDocsStrategy;

    public Strategy getStrategy(Envelope envelope, CaseDetails caseDetails) {
        Strategy strategy = null;

        switch (envelope.classification) {
            case SUPPLEMENTARY_EVIDENCE:
                strategy = attachDocsStrategy;

                break;
            case EXCEPTION:
            case NEW_APPLICATION:
            default:
                break;
        }

        return strategy;
    }

    StrategyContainer() {
        // utility class constructor
    }
}
