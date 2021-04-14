package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import java.util.List;

public class EventSummaryCreator {

    private static final String EVENT_SUMMARY_REPLACE = "{XX}";
    private static final String EVENT_SUMMARY_TRIM_DOTS = "...";
    private static String EVENT_SUMMARY_TEMP =
        "Attaching exception record(%d) document numbers:" + EVENT_SUMMARY_REPLACE + " to case:%d";
    private static int EVENT_SUMMARY_MAX_SIZE = 1024;

    private EventSummaryCreator() {
    }

    public static String createEventSummary(
        Long caseId,
        Long exceptionRecordId,
        List<String> documentNumberList
    ) {

        String eventSummary = String.format(
            EVENT_SUMMARY_TEMP,
            exceptionRecordId,
            caseId
        );

        int remainingSize = EVENT_SUMMARY_MAX_SIZE - eventSummary.length();
        String documentNumbers = documentNumberList.toString();
        if (documentNumbers.length() > remainingSize + EVENT_SUMMARY_REPLACE.length()) {
            documentNumbers = documentNumbers.substring(0, remainingSize) + EVENT_SUMMARY_TRIM_DOTS;
        }

        return eventSummary.replace(EVENT_SUMMARY_REPLACE, documentNumbers);
    }
}
