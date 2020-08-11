package uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Action that was done on a case.
 *
 * <p>Values from this enum are used in CCD case definitions ('action' field of BulkScanEnvelope complex type)</p>
 */
public enum CaseAction {
    @JsonProperty("create")
    CREATE,
    @JsonProperty("update")
    UPDATE
}
