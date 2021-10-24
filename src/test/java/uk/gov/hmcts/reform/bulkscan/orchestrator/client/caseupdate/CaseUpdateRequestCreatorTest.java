package uk.gov.hmcts.reform.bulkscan.orchestrator.client.caseupdate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.caseupdate.model.request.CaseUpdateDetails;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.caseupdate.model.request.CaseUpdateRequest;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.request.ScannedDocument;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.shared.DocumentMapper;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.internal.ExceptionRecord;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Document;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Envelope;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.OcrDataField;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.client.SampleData.sampleCaseDetails;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.client.SampleData.sampleEnvelope;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.client.SampleData.sampleExceptionRecord;

@ExtendWith(MockitoExtension.class)
class CaseUpdateRequestCreatorTest {

    @Mock DocumentMapper docMapper;

    CaseUpdateRequestCreator reqCreator;

    @BeforeEach
    void setUp() {
        this.reqCreator = new CaseUpdateRequestCreator(docMapper);
    }

    @Test
    void should_create_request_from_exception_record_correctly() {
        // given
        ExceptionRecord exceptionRecord = sampleExceptionRecord();
        CaseDetails caseDetails = sampleCaseDetails();

        // when
        CaseUpdateRequest request = reqCreator.create(exceptionRecord, caseDetails);

        // then
        assertThat(request.isAutomatedProcess).isFalse(); // always false when creating it from exception record
        assertThat(request.caseDetails.id).isEqualTo(caseDetails.getId().toString());
        assertThat(request.caseDetails.caseTypeId).isEqualTo(caseDetails.getCaseTypeId());
        assertThat(request.caseDetails.data).isEqualTo(caseDetails.getData());

        assertCaseUpdateDetailsMappedCorrectly(request.caseUpdateDetails, exceptionRecord);

        assertThat(request.exceptionRecord)
            .usingRecursiveComparison()
            .ignoringFields(
                "exceptionRecordId",
                "exceptionRecordCaseTypeId",
                "isAutomatedProcess"
            )
            .isEqualTo(exceptionRecord);

        assertThat(request.exceptionRecord.id).isEqualTo(exceptionRecord.id);
        assertThat(request.exceptionRecord.caseTypeId).isEqualTo(exceptionRecord.caseTypeId);
    }

    @Test
    void should_create_request_from_envelope_correctly() {
        // given
        var doc1 = mock(Document.class);
        var doc2 = mock(Document.class);

        var mappedDoc1 = mock(ScannedDocument.class);
        var mappedDoc2 = mock(ScannedDocument.class);

        given(docMapper.toScannedDoc(doc1, "jurisdiction1", true)).willReturn(mappedDoc1);
        given(docMapper.toScannedDoc(doc2, "jurisdiction1", true)).willReturn(mappedDoc2);

        Envelope envelope = sampleEnvelope(
            asList(
                new OcrDataField("name-1", "value-1"),
                new OcrDataField("name-2", "value-2")
            ),
            asList(
                doc1,
                doc2
            )
        );
        CaseDetails caseDetails = sampleCaseDetails();


        // when
        CaseUpdateRequest result = reqCreator.create(envelope, caseDetails);

        // then
        assertThat(result.isAutomatedProcess).isTrue(); // always true when creating it from envelope
        assertThat(result.exceptionRecord).isNull();

        assertThat(result.caseDetails.id).isEqualTo(caseDetails.getId().toString());
        assertThat(result.caseDetails.caseTypeId).isEqualTo(caseDetails.getCaseTypeId());
        assertThat(result.caseDetails.data).isEqualTo(caseDetails.getData());

        assertThat(result.caseUpdateDetails.exceptionRecordId).isNull();
        assertThat(result.caseUpdateDetails.exceptionRecordCaseTypeId).isNull();
        assertThat(result.caseUpdateDetails.envelopeId).isEqualTo(envelope.id);
        assertThat(result.caseUpdateDetails.poBox).isEqualTo(envelope.poBox);
        assertThat(result.caseUpdateDetails.poBoxJurisdiction).isEqualTo(envelope.jurisdiction);
        assertThat(result.caseUpdateDetails.formType).isEqualTo(envelope.formType);
        assertThat(result.caseUpdateDetails.deliveryDate).isEqualTo(toLocalDateTime(envelope.deliveryDate));
        assertThat(result.caseUpdateDetails.openingDate).isEqualTo(toLocalDateTime(envelope.openingDate));
        assertThat(result.caseUpdateDetails.ocrDataFields).hasSize(2);
        assertThat(result.caseUpdateDetails.ocrDataFields.get(0).name).isEqualTo("name-1");
        assertThat(result.caseUpdateDetails.ocrDataFields.get(1).name).isEqualTo("name-2");
        assertThat(result.caseUpdateDetails.scannedDocuments)
            .containsExactly(
                mappedDoc1,
                mappedDoc2
            );
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

    private LocalDateTime toLocalDateTime(Instant instant) {
        return instant == null
            ? null
            : ZonedDateTime.ofInstant(instant, ZoneId.systemDefault()).toLocalDateTime();
    }
}
