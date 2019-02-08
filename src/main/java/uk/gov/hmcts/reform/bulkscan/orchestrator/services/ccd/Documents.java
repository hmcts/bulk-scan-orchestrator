package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.apache.commons.lang3.StringUtils;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import javax.annotation.Nonnull;
import javax.validation.constraints.NotNull;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.stream.Collectors.toSet;

final class Documents {
    private static final String SCANNED_DOCUMENTS = "scannedDocuments";

    private Documents() {
    }

    private static Set<String> findDuplicates(List<Map<String, Object>> exceptionDocuments,
                                              List<Map<String, Object>> existingDocuments) {
        return Sets.intersection(
            getDocumentIdSet(existingDocuments),
            getDocumentIdSet(exceptionDocuments)
        );
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
            .map(Documents::getDocumentId)
            .filter(StringUtils::isNotEmpty)
            .collect(toSet());
    }

    private static String getDocumentId(Map<String, Object> document) {
        return Optional.ofNullable(document)
            .map(doc -> doc.get("value"))
            .filter(item -> item instanceof Map)
            .map(map -> ((Map) map).get("controlNumber"))
            .filter(item -> item instanceof String)
            .map(item -> (String) item)
            .orElse("");
    }

    static List<String> getDocumentNumbers(List<Map<String, Object>> documents) {
        return documents
            .stream()
            .map(Documents::getDocumentId)
            .collect(toImmutableList());
    }

    @Nonnull
    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> getScannedDocuments(CaseDetails theCase) {
        return (List<Map<String, Object>>)
            Optional.ofNullable(theCase.getData())
                .map(map -> map.get(SCANNED_DOCUMENTS))
                .orElseGet(Lists::newArrayList);
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
