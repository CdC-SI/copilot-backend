package zas.admin.zec.backend.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;

public class JsonSchemaBuilder {

    private JsonSchemaBuilder() {}

    public static String buildFlatJsonSchema(List<String> fields) {
        var mapper = new ObjectMapper();
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = mapper.createObjectNode();
        for (String field : fields) {
            properties.set(field, mapper.createObjectNode().put("type", "string"));
        }
        schema.set("properties", properties);

        ArrayNode required = mapper.createArrayNode();
        fields.forEach(required::add);
        schema.set("required", required);
        schema.put("additionalProperties", false);

        return schema.toPrettyString();
    }

}
