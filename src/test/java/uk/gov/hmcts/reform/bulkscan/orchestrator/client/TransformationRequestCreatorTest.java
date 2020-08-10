package uk.gov.hmcts.reform.bulkscan.orchestrator.client;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.client.SampleData.sampleExceptionRecord;

class TransformationRequestCreatorTest {

    @Test
    void should_set_all_fields_in_the_request_correctly() {
        // given
        var exceptionRecord = sampleExceptionRecord();

        // when
        var transformationRequest = new TransformationRequestCreator().create(exceptionRecord);

        // then
        assertThat(transformationRequest)
            .usingRecursiveComparison()
            .ignoringFields("exceptionRecordCaseTypeId", "exceptionRecordId", "isAutomatedProcess")
            .isEqualTo(exceptionRecord);

        assertThat(transformationRequest.isAutomatedProcess).isFalse();
        assertThat(transformationRequest.exceptionRecordCaseTypeId).isEqualTo(exceptionRecord.caseTypeId);
        assertThat(transformationRequest.exceptionRecordId).isEqualTo(exceptionRecord.id);
    }
}
