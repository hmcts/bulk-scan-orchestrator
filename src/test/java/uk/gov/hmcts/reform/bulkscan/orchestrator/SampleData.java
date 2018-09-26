package uk.gov.hmcts.reform.bulkscan.orchestrator;

import org.json.JSONArray;
import org.json.JSONObject;

import static java.util.Arrays.asList;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Classification.NEW_APPLICATION;

public class SampleData {

    public static String envelopeJson() throws Exception {
        return new JSONObject()
            .put("id", "eb9c3598-35fc-424e-b05a-902ee9f11d56")
            .put("case_ref", "ABC123")
            .put("jurisdiction", "SSCS")
            .put("classification", NEW_APPLICATION)
            .put("doc_urls", new JSONArray(asList("a", "b")))
            .toString();
    }

    private SampleData() {
        // util class
    }
}
