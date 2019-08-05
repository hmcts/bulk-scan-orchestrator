package uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation;

import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.request.DocumentType;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.request.ExceptionRecord;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.request.OcrDataField;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.request.ScannedDocument;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.response.CaseCreationDetails;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.response.ErrorResponse;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.response.InvalidExceptionRecordResponse;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.response.SuccessfulTransformationResponse;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Classification;

import java.time.Instant;
import java.util.Collections;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

//TODO: Delete this test when the models are used in the Transformation client
public class TransformationDataTest {

    @Test
    public void testTransformationSuccessfulResponse() {
        SuccessfulTransformationResponse response = new SuccessfulTransformationResponse(
            new CaseCreationDetails("Bulk_Scanned", "createCase", null),
            Collections.emptyList()
        );

        assertThat(response.warnings).isEmpty();
        assertThat(response.caseCreationDetails).isNotNull();
    }

    @Test
    public void testInvalidFormatTransformationResponse() {
        InvalidExceptionRecordResponse response = new InvalidExceptionRecordResponse(
            singletonList("Invalid Exception Record"),
            Collections.emptyList()
        );

        assertThat(response.errors).isNotEmpty();
        assertThat(response.warnings).isEmpty();
    }

    @Test
    public void testTransformationErrorResponse() {
        ErrorResponse response = new ErrorResponse(
            singletonList("Invalid Exception Record")
        );

        assertThat(response.errors).isNotEmpty();
    }

    @Test
    public void testTransformationRequest() {
        ExceptionRecord exceptionRecord = new ExceptionRecord(
            "Bulk_Scanned",
            "poBox",
            "poBoxJurisdiction",
            Classification.NEW_APPLICATION,
            Instant.now(),
            Instant.now(),
            singletonList(
                new ScannedDocument(
                    DocumentType.CHERISHED,
                    "subtype",
                    "url",
                    "controlNumber",
                    "fileName",
                    Instant.now(),
                    Instant.now()
                )
            ),
            singletonList(new OcrDataField("name1", "value1"))
        );

        assertThat(exceptionRecord).isNotNull();
        assertThat(exceptionRecord.scannedDocuments).hasSize(1);
        assertThat(exceptionRecord.ocrDataFields).hasSize(1);
    }
}
