package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.util.ExceptionRecordAttachDocumentConnectives;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import javax.validation.constraints.NotNull;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.Documents.getScannedDocuments;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.Documents.removeAlreadyAttachedDocuments;

class DocumentsTest {
    private static final String SCANNED_DOCUMENTS = "scannedDocuments";
    private static final String DOCUMENT_NUMBER = "id";

    @Test
    void should_remove_all_documents_from_exception_record_because_all_of_them_are_already_in_target_case() {
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
    void should_remove_overlapping_documents_from_exception_record_because_they_are_already_in_target_case() {
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
    void should_leave_all_documents_in_exception_record_because_there_is_nothing_matching_in_target_case() {
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

    private static Object[][] documentDuplicateTestParam() {
        return new Object[][]{
            {createCaseDetailsWith(), createDcnList(), ImmutableSet.<String>of()},
            {createCaseDetailsWith(1, 2, 3, 4, 5), createDcnList(6, 7, 8, 9), ImmutableSet.of()},
            {createCaseDetailsWith(1, 2, 3, 4, 5), createDcnList(6, 3, 4, 8, 9), ImmutableSet.of(3, 4)}
        };
    }

    @NotNull
    private Set<String> asStringSet(Set<Integer> duplicates) {
        return duplicates.stream().map(String::valueOf).collect(toSet());
    }

    @NotNull
    private static CaseDetails createCaseDetailsWith(Integer... dcns) {
        return CaseDetails.builder().data(ImmutableMap.of(SCANNED_DOCUMENTS, createDcnList(dcns))).build();
    }

    private static List<Map<String, Object>> createDcnList(Integer... dcns) {
        return Stream.of(dcns)
            .map(String::valueOf)
            .map(dcn -> ImmutableMap.<String, Object>of(
                DOCUMENT_NUMBER, UUID.randomUUID().toString(),
                "value", ImmutableMap.of("controlNumber", dcn)
                )
            )
            .collect(toImmutableList());
    }

    @ParameterizedTest
    @MethodSource("documentDuplicateTestParam")
    @DisplayName("Check the different for duplicates between the two lists.")
    void findDuplicatesTest(CaseDetails theCase,
                            List<Map<String, Object>> exceptionRecords,
                            Set<Integer> duplicates) {
        ExceptionRecordAttachDocumentConnectives erDocumentConnectives = Documents.calculateDocumentConnectives(
            exceptionRecords,
            getScannedDocuments(theCase)
        );

        if (duplicates.isEmpty()) {
            assertThat(erDocumentConnectives.hasDuplicatesAndMissing()).isFalse();
        } else {
            assertThat(erDocumentConnectives.getExistingInTargetCase()).isEqualTo(asStringSet(duplicates));
        }
    }
}
