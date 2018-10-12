package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.strategy;

import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Classification;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;

public final class StrategyContainer {

    private static final Set<Strategy> AVAILABLE_STRATEGIES = Collections.emptySortedSet();

    public static Strategy getStrategy(Envelope envelope, CaseDetails caseDetails) {
        return AVAILABLE_STRATEGIES
            .stream()
            .filter(strategy -> evaluateStrategy(strategy, envelope.classification, caseDetails))
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Strategy not found"));// replace with default aka exception record
    }

    private static boolean evaluateStrategy(
        Expression expression,
        Classification classification,
        CaseDetails caseDetails
    ) {
        return expression.isStrategyEligible(
            classification,
            Optional.ofNullable(caseDetails).isPresent()
        );
    }

    private StrategyContainer() {
        // utility class constructor
    }
}
