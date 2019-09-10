package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import io.vavr.control.Either;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.request.ExceptionRecord;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CreateCaseCallbackServiceTest {

    private static final String EVENT_ID = "createCase";
    private static final CreateCaseCallbackService SERVICE = new CreateCaseCallbackService();

    @Test
    void should_not_allow_to_process_callback_in_case_wrong_event_id_is_received() {
        Either<List<String>, ExceptionRecord> output = SERVICE.process(null, "some event");

        assertThat(output.isLeft()).isTrue();
        assertThat(output.getLeft()).containsOnly("The some event event is not supported. Please contact service team");
    }

    @Test
    void should_report_all_errors_when_null_is_provided_as_case_details() {
        Either<List<String>, ExceptionRecord> output = SERVICE.process(null, EVENT_ID);

        assertThat(output.isLeft()).isTrue();
        assertThat(output.getLeft()).containsOnly(
            "Missing caseType",
            "Internal Error: invalid jurisdiction supplied: null",
            "Missing journeyClassification"
        );
    }

    @Test
    void should_successfully_create_exception_record_with_documents_and_ocr_data_for_transformation_client() {
        // given
        Map<String, Object> data = new HashMap<>();
        // putting 6 via `ImmutableMap` is available from Java 9
        data.put("poBox", "12345");
        data.put("journeyClassification", "EXCEPTION");
        data.put("deliveryDate", "2019-09-06T15:30:03.000Z");
        data.put("openingDate", "2019-09-06T15:30:04.000Z");
        data.put("scannedDocuments", TestCaseBuilder.document("https://url", "some doc"));
        data.put("scanOCRData", TestCaseBuilder.ocrDataEntry("some key", "some value"));

        CaseDetails caseDetails = TestCaseBuilder.createCaseWith(builder -> builder
            .caseTypeId("some case type")
            .jurisdiction("some jurisdiction")
            .data(data)
        );

        // when
        Either<List<String>, ExceptionRecord> output = SERVICE.process(caseDetails, EVENT_ID);

        // then
        assertThat(output.isRight()).isTrue();
        assertThat(output.get().scannedDocuments).hasSize(0);
        assertThat(output.get().ocrDataFields).hasSize(0);
    }
}
