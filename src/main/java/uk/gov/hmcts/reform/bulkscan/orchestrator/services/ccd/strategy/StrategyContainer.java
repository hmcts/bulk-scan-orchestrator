package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.strategy;

import com.google.common.collect.ImmutableSet;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Classification;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import javax.annotation.PostConstruct;

/**
 * Container class to hold availableStrategies strategies enabled by this project.
 * In order to enable one must do:
 * <ul>
 *     <li>implement {@link AbstractStrategy}</li>
 *     <li>include {@code @Resource(name = "strategy-name") private Strategy someStrategy;}</li>
 *     <li>stick resource in {@link this#availableStrategies} via {@link this#setUpStrategies()}</li>
 * </ul>
 */
@Component
public class StrategyContainer {

    private Set<Strategy> availableStrategies = Collections.emptySortedSet();

    @PostConstruct
    public void setUpStrategies() {
        availableStrategies = new LinkedHashSet<>(ImmutableSet.of(
        // set of strategies
        ));
    }

    public Strategy getStrategy(Envelope envelope, CaseDetails caseDetails) {
        return availableStrategies
            .stream()
            .filter(strategy -> strategy.isStrategyEligible(envelope.classification, caseDetails != null))
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Strategy not found"));// replace with default aka exception record
    }

    private StrategyContainer() {
        // utility class constructor
    }
}
