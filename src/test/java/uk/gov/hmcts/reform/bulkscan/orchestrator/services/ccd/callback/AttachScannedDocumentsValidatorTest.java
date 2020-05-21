package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.callback;

import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThatCode;

class AttachScannedDocumentsValidatorTest {

    private static final AttachScannedDocumentsValidator VALIDATOR = new AttachScannedDocumentsValidator();

    @Test
    void should_do_nothing_when_all_documents_are_already_attached() {
        // given
        String exceptionRecordReference = "REF";
        List<Map<String, Object>> exceptionRecordDocuments = singletonList(
            singletonMap("value", singletonMap("controlNumber", "e-123"))
        );
        List<Map<String, Object>> targetCaseDocuments = singletonList(
            singletonMap("value", ImmutableMap.of(
                "controlNumber", "e-123",
                "exceptionRecordReference", exceptionRecordReference
            ))
        );

        // when, then
        assertThatCode(() -> VALIDATOR.verifyExceptionRecordAddsNoDuplicates(
            targetCaseDocuments,
            exceptionRecordDocuments,
            exceptionRecordReference,
            "target-ref"
        )).doesNotThrowAnyException();
    }

    @Test
    void should_do_nothing_when_no_documents_are_attached() {
        // given
        List<Map<String, Object>> exceptionRecordDocuments = singletonList(
            singletonMap("value", singletonMap("controlNumber", "e-123"))
        );
        List<Map<String, Object>> targetCaseDocuments = singletonList(
            singletonMap("value", singletonMap("controlNumber", "c-123"))
        );

        // when, then
        assertThatCode(() -> VALIDATOR.verifyExceptionRecordAddsNoDuplicates(
            targetCaseDocuments,
            exceptionRecordDocuments,
            "exception-ref",
            "target-ref"
        )).doesNotThrowAnyException();
    }

    @Test
    void should_throw_exception_when_document_is_already_present_in_target_case() {
        // given
        List<Map<String, Object>> exceptionRecordDocuments = singletonList(
            singletonMap("value", singletonMap("controlNumber", "e-123"))
        );
        List<Map<String, Object>> targetCaseDocuments = singletonList(
            singletonMap("value", singletonMap("controlNumber", "e-123"))
        );

        // when, then
        assertThatCode(() -> VALIDATOR.verifyExceptionRecordAddsNoDuplicates(
            targetCaseDocuments,
            exceptionRecordDocuments,
            "exception-ref",
            "target-ref"
        )).isInstanceOf(DuplicateDocsException.class);
    }
}
