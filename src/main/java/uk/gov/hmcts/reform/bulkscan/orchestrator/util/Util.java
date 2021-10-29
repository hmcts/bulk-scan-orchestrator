package uk.gov.hmcts.reform.bulkscan.orchestrator.util;

public final class Util {
    private Util() {
        // utility class
    }

    public static String getDocumentUuid(String documentUrl) {
        return documentUrl.substring(documentUrl.lastIndexOf("/") + 1);
    }
}
