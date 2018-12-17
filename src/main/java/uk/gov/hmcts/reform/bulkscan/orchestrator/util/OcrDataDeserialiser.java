package uk.gov.hmcts.reform.bulkscan.orchestrator.util;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public class OcrDataDeserialiser extends StdDeserializer<Map<String, String>> {

    protected OcrDataDeserialiser() {
        super(LinkedHashMap.class);
    }

    @Override
    public Map<String, String> deserialize(
        JsonParser p,
        DeserializationContext ctxt
    ) throws IOException, JsonProcessingException {
        JsonNode node = p.readValueAsTree();

        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        Map<String, String> ocrData = new LinkedHashMap<>();

        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            ocrData.put(entry.getKey(), entry.getValue().asText());
        }

        return ocrData;
    }
}
