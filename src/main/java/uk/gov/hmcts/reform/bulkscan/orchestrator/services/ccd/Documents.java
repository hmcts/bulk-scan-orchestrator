package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import jakarta.annotation.Nonnull;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

public final class Documents {
    private static final String SCANNED_DOCUMENTS = "scannedDocuments";

    private Documents() {
    }

    static List<Map<String, Object>> removeAlreadyAttachedDocuments(
        List<Map<String, Object>> exceptionRecordDocuments,
        List<Map<String, Object>> targetCaseDocuments,
        String exceptionRecordCcdRef
    ) {
        Set<String> documentDcnsFromTargetCase = targetCaseDocuments
            .stream()
            .filter(doc -> Objects.equals(getExceptionRecordReference(doc), exceptionRecordCcdRef))
            .map(Documents::getDocumentId)
            .collect(toSet());

        return exceptionRecordDocuments
            .stream()
            .filter(doc -> !documentDcnsFromTargetCase.contains(getDocumentId(doc)))
            .collect(toList());
    }

    public static String getDocumentId(Map<String, Object> document) {
        return Optional.ofNullable(document)
            .map(doc -> doc.get("value"))
            .filter(Map.class::isInstance)
            .map(map -> ((Map) map).get("controlNumber"))
            .filter(String.class::isInstance)
            .map(String.class::cast)
            .orElse("");
    }

    public static String getExceptionRecordReference(Map<String, Object> document) {
        return (String) Optional.ofNullable(document)
            .map(doc -> doc.get("value"))
            .map(map -> ((Map) map).get("exceptionRecordReference"))
            .orElse(null);
    }

    static List<String> getDocumentNumbers(List<Map<String, Object>> documents) {
        return documents
            .stream()
            .map(Documents::getDocumentId)
            .collect(toImmutableList());
    }

    @Nonnull
    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> getScannedDocuments(CaseDetails theCase) {
        return (List<Map<String, Object>>)
            Optional.ofNullable(theCase.getData())
                .map(map -> map.get(SCANNED_DOCUMENTS))
                .orElseGet(Lists::newArrayList);
    }


    static List<Object> concatDocuments(
        List<Map<String, Object>> exceptionDocuments,
        List<Map<String, Object>> existingDocuments
    ) {
        return ImmutableList.builder()
            .addAll(existingDocuments)
            .addAll(exceptionDocuments)
            .build();
    }


}
