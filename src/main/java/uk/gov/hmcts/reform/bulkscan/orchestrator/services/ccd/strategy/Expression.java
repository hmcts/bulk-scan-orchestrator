package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.strategy;

import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Classification;

/**
 * For strategies to use privately only to decide whether it meets the conditions for execution.
 */
interface Expression {

    boolean isStrategyEligible(Classification classification, boolean caseExists);
}
