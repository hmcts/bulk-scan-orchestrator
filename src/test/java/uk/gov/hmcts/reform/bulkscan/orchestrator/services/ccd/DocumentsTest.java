package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.Documents.getScannedDocuments;

class DocumentsTest {
    private static final String SCANNED_DOCUMENTS = "scannedDocuments";
    private static final String DOCUMENT_NUMBER = "id";

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
                "value", ImmutableMap.of("controlNumber", dcn))
            )
            .collect(toImmutableList());
    }

    @ParameterizedTest
    @MethodSource("documentDuplicateTestParam")
    @DisplayName("Check the different for duplicates between the two lists.")
    void findDuplicatesTest(CaseDetails theCase,
                            List<Map<String, Object>> exceptionRecords,
                            Set<Integer> duplicates) {
        AtomicReference<Set<String>> result = new AtomicReference<>();
        Documents.checkForDuplicatesOrElse(exceptionRecords, getScannedDocuments(theCase), result::set);

        if (duplicates.isEmpty()) {
            assertThat(result.get()).isNull();
        } else {
            assertThat(result.get()).isEqualTo(asStringSet(duplicates));
        }
    }
}
