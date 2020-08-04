package uk.gov.hmcts.reform.bulkscan.orchestrator.client;

import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.caseupdate.model.request.CaseUpdateDetails;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.caseupdate.model.request.CaseUpdateRequest;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.internal.ExceptionRecord;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.client.SampleData.sampleCaseDetails;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.client.SampleData.sampleExceptionRecord;

public class CaseUpdateRequestCreatorTest {

    @Test
    void create_should_map_all_request_fields_correctly() {
        // given
        ExceptionRecord exceptionRecord = sampleExceptionRecord();
        CaseDetails caseDetails = sampleCaseDetails();

        // when
        CaseUpdateRequest request = new CaseUpdateRequestCreator().create(exceptionRecord, caseDetails);

        // then
        assertThat(request.isAutomatedProcess).isFalse();

        assertThat(request.caseDetails.id).isEqualTo(caseDetails.getId().toString());
        assertThat(request.caseDetails.caseTypeId).isEqualTo(caseDetails.getCaseTypeId());
        assertThat(request.caseDetails.data).isEqualTo(caseDetails.getData());

        assertCaseUpdateDetailsMappedCorrectly(request.caseUpdateDetails, exceptionRecord);

        assertThat(request.exceptionRecord).isEqualToIgnoringGivenFields(
            exceptionRecord,
            "exceptionRecordId",
            "exceptionRecordCaseTypeId",
            "isAutomatedProcess"
        );

        assertThat(request.exceptionRecord.exceptionRecordId).isEqualTo(exceptionRecord.id);
        assertThat(request.exceptionRecord.exceptionRecordCaseTypeId).isEqualTo(exceptionRecord.caseTypeId);
        assertThat(request.exceptionRecord.isAutomatedProcess).isFalse();
    }

    private void assertCaseUpdateDetailsMappedCorrectly(
        CaseUpdateDetails caseUpdateDetails,
        ExceptionRecord exceptionRecord
    ) {
        assertThat(caseUpdateDetails.deliveryDate).isEqualTo(exceptionRecord.deliveryDate);
        assertThat(caseUpdateDetails.envelopeId).isEqualTo(exceptionRecord.envelopeId);
        assertThat(caseUpdateDetails.exceptionRecordCaseTypeId).isEqualTo(exceptionRecord.caseTypeId);
        assertThat(caseUpdateDetails.exceptionRecordId).isEqualTo(exceptionRecord.id);
        assertThat(caseUpdateDetails.formType).isEqualTo(exceptionRecord.formType);
        assertThat(caseUpdateDetails.ocrDataFields).isEqualTo(exceptionRecord.ocrDataFields);
        assertThat(caseUpdateDetails.openingDate).isEqualTo(exceptionRecord.openingDate);
        assertThat(caseUpdateDetails.poBox).isEqualTo(exceptionRecord.poBox);
        assertThat(caseUpdateDetails.poBoxJurisdiction).isEqualTo(exceptionRecord.poBoxJurisdiction);
        assertThat(caseUpdateDetails.scannedDocuments).isEqualTo(exceptionRecord.scannedDocuments);
    }
}
