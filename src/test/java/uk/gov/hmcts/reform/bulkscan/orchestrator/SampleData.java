package uk.gov.hmcts.reform.bulkscan.orchestrator;

import org.json.JSONArray;
import org.json.JSONObject;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Classification;

import static java.util.Arrays.asList;

public class SampleData {

    public static String envelopeJson() throws Exception {
        return new JSONObject()
            .put("id", "")
            .put("case_ref", "eb9c3598-35fc-424e-b05a-902ee9f11d56")
            .put("jurisdiction", "SSCS")
            .put("classification", Classification.NEW_APPLICATION)
            .put("doc_urls", new JSONArray(asList("a", "b")))
            .toString();
    }
}
