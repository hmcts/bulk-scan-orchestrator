package uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class TransformationResponseTestData {

    private TransformationResponseTestData() {
        // utility class
    }

    static JSONObject successResponse() throws JSONException {
        return new JSONObject()
            .put("case_creation_details", new JSONObject()
                .put("case_type_id", "some_case_type")
                .put("event_id", "createCase")
                .put(
                    "case_data",
                    new JSONObject()
                        .put("case_field", "some value")
                        .put("form_type", "d8")
                ))
            .put("warnings", new JSONArray());
    }

    static JSONObject errorResponse() throws Exception {
        return new JSONObject()
            .put("errors", new JSONArray().put("field1 is missing"))
            .put("warnings", new JSONArray().put("field2 is missing"));
    }

    static JSONObject invalidDataResponse() throws Exception {
        return new JSONObject()
            .put("errors", new JSONArray().put("field1 is missing"));
    }
}
