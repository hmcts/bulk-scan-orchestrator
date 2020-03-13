package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.apache.commons.lang3.StringUtils;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.util.ExceptionRecordAttachDocumentConnectives;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.validation.constraints.NotNull;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.stream.Collectors.toSet;

public final class Documents {
    private static final String SCANNED_DOCUMENTS = "scannedDocuments";

    private Documents() {
    }

    static ExceptionRecordAttachDocumentConnectives calculateDocumentConnectives(
        List<Map<String, Object>> exceptionDocuments,
        List<Map<String, Object>> existingDocuments
    ) {
        Set<String> exceptionRecordDocumentIds = getDocumentIdSet(exceptionDocuments);
        Set<String> existingCaseDocumentIds = getDocumentIdSet(existingDocuments);

        return new ExceptionRecordAttachDocumentConnectives(
            Sets.intersection(
                exceptionRecordDocumentIds,
                existingCaseDocumentIds
            ),
            exceptionRecordDocumentIds.size()
        );
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
