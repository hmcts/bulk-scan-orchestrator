package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import org.jetbrains.annotations.NotNull;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import javax.annotation.Nonnull;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.stream.Collectors.toSet;

final class Documents {
    private static final String SCANNED_DOCUMENTS = "scannedDocuments";
    private static final String SCAN_RECORDS = "scanRecords";
    private static final String DOCUMENT_NUMBER = "documentNumber";

    private Documents() {
    }

    private static Set<String> findDuplicates(List<Map<String, Object>> exceptionDocuments,
                                              List<Map<String, Object>> existingDocuments) {
        return Sets.intersection(getDocumentIdSet(existingDocuments), getDocumentIdSet(exceptionDocuments));
    }

    static void checkForDuplicatesOrElse(List<Map<String, Object>> exceptionDocuments,
                                         List<Map<String, Object>> existingDocuments,
                                         Consumer<Set<String>> duplicatesExist) {
        Set<String> ids = findDuplicates(exceptionDocuments, existingDocuments);
        if (!ids.isEmpty()) {
            duplicatesExist.accept(ids);
        }
    }

    @NotNull
    private static Set<String> getDocumentIdSet(List<Map<String, Object>> existingDocuments) {
        return existingDocuments.stream()
            .map(document -> (String) document.get(DOCUMENT_NUMBER))
            .collect(toSet());
    }

    static List<String> getDocumentNumbers(List<Map<String, Object>> documents) {
        return documents
            .stream()
            .map(doc -> (String) doc.get(DOCUMENT_NUMBER))
            .collect(toImmutableList());
    }

    @Nonnull
    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> getScannedDocuments(CaseDetails theCase) {
        //TODO check that the SCANNED_DOCUMENTS exists first or return a new list ?
        return (List<Map<String, Object>>) theCase.getData().get(SCANNED_DOCUMENTS);
    }

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> getScannedRecords(Map<String, Object> exceptionData) {
        return (List<Map<String, Object>>) exceptionData.get(SCAN_RECORDS);
    }

    static Map<String, Object> insertNewRecords(List<Map<String, Object>> exceptionDocuments,
                                                List<Map<String, Object>> existingDocuments) {
        ImmutableList<Object> documentList = ImmutableList.builder()
            .addAll(existingDocuments)
            .addAll(exceptionDocuments)
            .build();
        return ImmutableMap.of(SCANNED_DOCUMENTS, documentList);
    }


}
