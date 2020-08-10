package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import uk.gov.hmcts.reform.bulkscan.orchestrator.model.internal.ExceptionRecord;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification;

import java.util.List;
import java.util.Map;

/**
 * Information received in the callback call for the attach event.
 */
class AttachToCaseEventData {
    public final String exceptionRecordJurisdiction;
    public final String service;
    public final String targetCaseRef;
    public final String targetCaseRefType;
    public final Long exceptionRecordId;
    public final List<Map<String, Object>> exceptionRecordDocuments;
    public final String idamToken;
    public final String userId;
    public final Classification classification;
    public final ExceptionRecord exceptionRecord;

    public AttachToCaseEventData(
        String exceptionRecordJurisdiction,
        String service,
        String targetCaseRefType,
        String targetCaseRef,
        Long exceptionRecordId,
        List<Map<String, Object>> exceptionRecordDocuments,
        String idamToken,
        String userId,
        Classification classification,
        ExceptionRecord exceptionRecord
    ) {
        this.exceptionRecordJurisdiction = exceptionRecordJurisdiction;
        this.service = service;
        this.targetCaseRefType = targetCaseRefType;
        this.targetCaseRef = targetCaseRef;
        this.exceptionRecordId = exceptionRecordId;
        this.exceptionRecordDocuments = exceptionRecordDocuments;
        this.idamToken = idamToken;
        this.userId = userId;
        this.classification = classification;
        this.exceptionRecord = exceptionRecord;
    }
}
