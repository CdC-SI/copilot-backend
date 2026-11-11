package zas.admin.zec.backend.tools;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;

public class JsonSchemaBuilder {

    private JsonSchemaBuilder() {}

    public static String buildFlatJsonSchema(List<String> fields) {
        var mapper = JsonMapper.builder().build();
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
