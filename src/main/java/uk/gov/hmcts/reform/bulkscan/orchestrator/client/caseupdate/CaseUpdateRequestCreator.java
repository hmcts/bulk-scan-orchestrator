package uk.gov.hmcts.reform.bulkscan.orchestrator.client.caseupdate;

import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.caseupdate.model.request.CaseUpdateDetails;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.caseupdate.model.request.CaseUpdateRequest;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.caseupdate.model.request.ExceptionRecord;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.caseupdate.model.request.ExistingCaseDetails;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.request.OcrDataField;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.shared.DocumentMapper;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Envelope;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;

@Service
public class CaseUpdateRequestCreator {

    private final DocumentMapper documentMapper;

    public CaseUpdateRequestCreator(DocumentMapper documentMapper) {
        this.documentMapper = documentMapper;
    }

    public CaseUpdateRequest create(
        uk.gov.hmcts.reform.bulkscan.orchestrator.model.internal.ExceptionRecord exceptionRecord,
        CaseDetails existingCaseDetails,
        boolean isAutomatedProcess
    ) {
        return new CaseUpdateRequest(
            createExceptionRecord(exceptionRecord),
            isAutomatedProcess,
            createCaseUpdateDetails(exceptionRecord),
            createExistingCaseDetails(existingCaseDetails)
        );
    }

    public CaseUpdateRequest create(Envelope envelope, CaseDetails existingCaseDetails) {
        return new CaseUpdateRequest(
            null,
            true,
            createCaseUpdateDetails(envelope),
            createExistingCaseDetails(existingCaseDetails)
        );
    }

    private ExceptionRecord createExceptionRecord(
        uk.gov.hmcts.reform.bulkscan.orchestrator.model.internal.ExceptionRecord exceptionRecord
    ) {
        return new ExceptionRecord(
            exceptionRecord.id,
            exceptionRecord.caseTypeId,
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

    private CaseUpdateDetails createCaseUpdateDetails(Envelope envelope) {
        return new CaseUpdateDetails(
            null,
            null,
            envelope.id,
            envelope.poBox,
            envelope.jurisdiction,
            envelope.formType,
            toLocalDateTime(envelope.deliveryDate),
            toLocalDateTime(envelope.openingDate),
            envelope.documents.stream().map(documentMapper::toScannedDoc).collect(Collectors.toList()),
            envelope
                .ocrData
                .stream()
                .map(field -> new OcrDataField(field.name, field.value))
                .collect(toList())
        );
    }

    private LocalDateTime toLocalDateTime(Instant instant) {
        if (instant == null) {
            return null;
        } else {
            return ZonedDateTime.ofInstant(instant, ZoneId.systemDefault()).toLocalDateTime();
        }
    }
}
