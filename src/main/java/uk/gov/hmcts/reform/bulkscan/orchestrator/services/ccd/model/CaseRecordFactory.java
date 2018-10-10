package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.model;

import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.model.caserecord.ExceptionRecord;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.model.caserecord.SupplementaryEvidenceRecord;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.util.HashMap;
import java.util.Map;

import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.model.CaseRecord.Construct;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.model.CaseRecord.Record;

public final class CaseRecordFactory {

    private static final Map<Record, Construct> AVAILABLE_MODELS = new HashMap<>();

    static {
        AVAILABLE_MODELS.put(Record.EXCEPTION_RECORD, ExceptionRecord::new);
        AVAILABLE_MODELS.put(Record.SUPPLEMENTARY_EVIDENCE, SupplementaryEvidenceRecord::new);
    }

    public static CaseDataCreator getCaseDataCreator(Envelope envelope, CaseDetails caseDetails) {
        Record record;

        switch (envelope.classification) {
            case SUPPLEMENTARY_EVIDENCE:
                record = Record.SUPPLEMENTARY_EVIDENCE;

                break;
            case EXCEPTION:
            case NEW_APPLICATION:
            default:
                record = Record.EXCEPTION_RECORD;

                break;
        }

        return AVAILABLE_MODELS.get(record).apply(envelope, caseDetails);
    }

    private CaseRecordFactory() {
        // utility class constructor
    }
}
