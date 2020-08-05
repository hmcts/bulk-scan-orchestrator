package uk.gov.hmcts.reform.bulkscan.orchestrator.client;

import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.request.TransformationRequest;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.internal.ExceptionRecord;

@Service
public class TransformationRequestCreator {

    public TransformationRequest create(ExceptionRecord exceptionRecord) {
        return new TransformationRequest(
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
}
