package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.callback;

import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.ExceptionRecordFields;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.util.Objects;

import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.ExceptionRecordFields.CONTAINS_PAYMENTS;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.YesNoFieldValues.YES;

public final class PaymentsHelper {

    public final boolean containsPayments;
    public final String envelopeId;

    private PaymentsHelper(boolean containsPayments, String envelopeId) {
        this.containsPayments = containsPayments;
        this.envelopeId = envelopeId;
    }

    public static PaymentsHelper create(CaseDetails caseDetails) {
        var containsPayments = Objects.equals(
            caseDetails.getData().get(CONTAINS_PAYMENTS),
            YES
        );

        return new PaymentsHelper(
            containsPayments,
            containsPayments ? (String) caseDetails.getData().get(ExceptionRecordFields.ENVELOPE_ID) : null
        );
    }
}
