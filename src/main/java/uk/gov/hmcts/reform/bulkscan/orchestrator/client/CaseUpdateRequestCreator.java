package uk.gov.hmcts.reform.bulkscan.orchestrator.client;

import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.caseupdate.model.request.CaseUpdateDetails;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.caseupdate.model.request.CaseUpdateRequest;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.caseupdate.model.request.ExistingCaseDetails;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.request.ExceptionRecord;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

@Service
public class CaseUpdateRequestCreator {

    public CaseUpdateRequest create(
        uk.gov.hmcts.reform.bulkscan.orchestrator.model.internal.ExceptionRecord exceptionRecord,
        CaseDetails existingCaseDetails
    ) {
        return new CaseUpdateRequest(
            createExceptionRecord(exceptionRecord),
            false,
            createCaseUpdateDetails(exceptionRecord),
            createExistingCaseDetails(existingCaseDetails)
        );
    }

    private ExceptionRecord createExceptionRecord(
        uk.gov.hmcts.reform.bulkscan.orchestrator.model.internal.ExceptionRecord exceptionRecord
    ) {
        return new ExceptionRecord(
            exceptionRecord.id,
            exceptionRecord.caseTypeId,
            exceptionRecord.envelopeId,
            false,
            exceptionRecord.poBox,
            exceptionRecord.poBoxJurisdiction,
            exceptionRecord.journeyClassification,
            exceptionRecord.formType,
            exceptionRecord.deliveryDate,
            exceptionRecord.openingDate,
            exceptionRecord.scannedDocuments,
            exceptionRecord.ocrDataFields
        );
    }

    private ExistingCaseDetails createExistingCaseDetails(CaseDetails caseDetails) {
        return new ExistingCaseDetails(
            caseDetails.getId().toString(),
            caseDetails.getCaseTypeId(),
            caseDetails.getData()
        );
    }

    private CaseUpdateDetails createCaseUpdateDetails(
        uk.gov.hmcts.reform.bulkscan.orchestrator.model.internal.ExceptionRecord exceptionRecord
    ) {
        return new CaseUpdateDetails(
            exceptionRecord.caseTypeId,
            exceptionRecord.id,
            exceptionRecord.envelopeId,
            exceptionRecord.poBox,
            exceptionRecord.poBoxJurisdiction,
            exceptionRecord.formType,
            exceptionRecord.deliveryDate,
            exceptionRecord.openingDate,
            exceptionRecord.scannedDocuments,
            exceptionRecord.ocrDataFields
        );
    }
}
