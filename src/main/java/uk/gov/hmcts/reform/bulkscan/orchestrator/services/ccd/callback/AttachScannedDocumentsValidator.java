package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.callback;

import com.google.common.collect.Sets;
import org.slf4j.Logger;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.Documents;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.DuplicateDocsException;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

import static java.util.stream.Collectors.toSet;
import static org.slf4j.LoggerFactory.getLogger;

public final class AttachScannedDocumentsValidator {

    private static final Logger log = getLogger(AttachScannedDocumentsValidator.class);

    private AttachScannedDocumentsValidator() {
        // utility class constructor
    }

    public static void verifyExceptionRecordAddsNoDuplicates(
        List<Map<String, Object>> targetCaseDocuments,
        List<Map<String, Object>> exceptionRecordDocuments,
        String exceptionRecordCcdRef,
        String targetCaseCcdRef
    ) {
        logIfDocumentsAreAlreadyAttachedToCaseFromER(
            targetCaseDocuments,
            exceptionRecordDocuments,
            exceptionRecordCcdRef,
            targetCaseCcdRef
        );

        Set<String> clashingDocumentDcns = getDcnsOfClashingDocuments(
            exceptionRecordCcdRef,
            exceptionRecordDocuments,
            targetCaseDocuments
        );

        if (!clashingDocumentDcns.isEmpty()) {
            // avoiding 120 line length rule
            String message = String.format(
                "Documents with following control numbers are already present in the case %s and cannot be added: %s",
                targetCaseCcdRef,
                String.join(", ", clashingDocumentDcns)
            );

            throw new DuplicateDocsException(message);
        }
    }

    private static void logIfDocumentsAreAlreadyAttachedToCaseFromER(
        List<Map<String, Object>> targetCaseDocuments,
        List<Map<String, Object>> exceptionRecordDocuments,
        String exceptionRecordCcdRef,
        String targetCaseCcdRef
    ) {
        Set<String> alreadyAttachedDocumentDcns = getDcnsOfDocumentsAlreadyAttachedToCaseFromER(
            exceptionRecordCcdRef,
            exceptionRecordDocuments,
            targetCaseDocuments
        );

        if (alreadyAttachedDocumentDcns.size() == exceptionRecordDocuments.size()) {
            log.warn(
                "All documents from exception record {} have already been attached to case {}",
                exceptionRecordCcdRef,
                targetCaseCcdRef
            );
        } else if (!alreadyAttachedDocumentDcns.isEmpty()) {
            log.warn(
                "Following documents have already been added from exception record {} to case {}: {}",
                exceptionRecordCcdRef,
                targetCaseCcdRef,
                alreadyAttachedDocumentDcns
            );
        }
    }

    private static Set<String> getDcnsOfDocumentsAlreadyAttachedToCaseFromER(
        String exceptionRecordId,
        List<Map<String, Object>> exceptionRecordDocuments,
        List<Map<String, Object>> targetCaseDocuments
    ) {
        return findDocumentsIntersection(
            doc -> Objects.equals(Documents.getExceptionRecordReference(doc), exceptionRecordId),
            exceptionRecordDocuments,
            targetCaseDocuments
        );
    }

    private static Set<String> getDcnsOfClashingDocuments(
        String exceptionRecordId,
        List<Map<String, Object>> exceptionRecordDocuments,
        List<Map<String, Object>> targetCaseDocuments
    ) {
        return findDocumentsIntersection(
            doc -> !Objects.equals(Documents.getExceptionRecordReference(doc), exceptionRecordId),
            exceptionRecordDocuments,
            targetCaseDocuments
        );
    }

    private static Set<String> findDocumentsIntersection(
        Function<Map<String, Object>, Boolean> filterOnTargetCaseDocuments,
        List<Map<String, Object>> exceptionRecordDocuments,
        List<Map<String, Object>> targetCaseDocuments
    ) {
        Set<String> dcnsOfCaseDocumentsFromOtherSources = targetCaseDocuments
            .stream()
            .filter(filterOnTargetCaseDocuments::apply)
            .map(Documents::getDocumentId)
            .collect(toSet());

        Set<String> dcnsOfExceptionRecordDocuments = exceptionRecordDocuments
            .stream()
            .map(Documents::getDocumentId)
            .collect(toSet());

        return Sets.intersection(dcnsOfCaseDocumentsFromOtherSources, dcnsOfExceptionRecordDocuments);
    }
}
