package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.Documents.removeAlreadyAttachedDocuments;

class DocumentsTest {

    @Test
    void should_remove_all_documents_from_exception_record_when_all_of_them_are_already_in_target_case() {
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

        // when
        List<Map<String, Object>> actualDocumentsToAttach = removeAlreadyAttachedDocuments(
            exceptionRecordDocuments,
            targetCaseDocuments,
            exceptionRecordReference
        );

        // then
        assertThat(actualDocumentsToAttach).asList().isEmpty();
    }

    @Test
    void should_remove_overlapping_documents_from_exception_record_when_they_are_already_in_target_case() {
        // given
        String exceptionRecordReference = "REF";
        List<Map<String, Object>> exceptionRecordDocuments = ImmutableList.of(
            singletonMap("value", singletonMap("controlNumber", "e-123")),
            singletonMap("value", singletonMap("controlNumber", "e-321"))
        );
        List<Map<String, Object>> targetCaseDocuments = singletonList(
            singletonMap("value", ImmutableMap.of(
                "controlNumber", "e-123",
                "exceptionRecordReference", exceptionRecordReference
            ))
        );

        // when
        List<Map<String, Object>> actualDocumentsToAttach = removeAlreadyAttachedDocuments(
            exceptionRecordDocuments,
            targetCaseDocuments,
            exceptionRecordReference
        );

        // then
        assertThat(actualDocumentsToAttach)
            .asList()
            .hasSize(1)
            .first()
            .isInstanceOfSatisfying(actualDocumentsToAttach.get(0).getClass(), document ->
                assertThat(document)
                    .hasFieldOrProperty("value")
                    .extracting("value")
                    .isInstanceOfSatisfying(document.getClass(), actualDocument ->
                        assertThat(actualDocument)
                            .hasFieldOrPropertyWithValue("controlNumber", "e-321")
                    )
            );
    }

    @Test
    void should_leave_all_documents_in_exception_record_when_there_is_nothing_matching_in_target_case() {
        // given
        List<Map<String, Object>> exceptionRecordDocuments = ImmutableList.of(
            singletonMap("value", singletonMap("controlNumber", "e-123")),
            singletonMap("value", singletonMap("controlNumber", "e-321"))
        );
        List<Map<String, Object>> targetCaseDocuments = singletonList(
            singletonMap("value", ImmutableMap.of(
                "controlNumber", "e-123",
                "exceptionRecordReference", "REF-1"
            ))
        );

        // when
        List<Map<String, Object>> actualDocumentsToAttach = removeAlreadyAttachedDocuments(
            exceptionRecordDocuments,
            targetCaseDocuments,
            "REF-2"
        );

        // then
        assertThat(actualDocumentsToAttach).asList().hasSize(2);
    }

    @Test
    void should_return_document_control_number_when_one_is_present() {
        // given
        String expected = "123";
        Map<String, Object> document = ImmutableMap.of("value", ImmutableMap.of("controlNumber", expected));

        // when
        String actual = Documents.getDocumentId(document);

        // then
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void should_return_empty_string_as_document_control_number_when_one_is_not_present() {
        // given
        Map<String, Object> document = ImmutableMap.of("value", emptyMap());

        // when
        String actual = Documents.getDocumentId(document);

        // then
        assertThat(actual).isEqualTo("");
    }

    @Test
    void should_return_exception_record_reference_when_one_is_present() {
        // given
        String ref = "123";
        Map<String, Object> document = ImmutableMap.of("value", ImmutableMap.of("exceptionRecordReference", ref));

        // when
        String actualRef = Documents.getExceptionRecordReference(document);

        // then
        assertThat(actualRef).isEqualTo(ref);
    }

    @Test
    void should_return_null_as_exception_record_reference_when_one_is_not_present() {
        // given
        Map<String, Object> document = ImmutableMap.of("value", emptyMap());

        // when
        String actualRef = Documents.getExceptionRecordReference(document);

        // then
        assertThat(actualRef).isNull();
    }
}
