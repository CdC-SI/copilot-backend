package zas.admin.zec.backend.tools;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class JsonSchemaBuilderTest {

    @Test
    void flatSchemaDeclaresEveryFieldAsARequiredString() {
        var schema = JsonMapper.builder().build().readTree(JsonSchemaBuilder.buildFlatJsonSchema(List.of("title", "summary")));

        assertEquals("object", schema.get("type").asString());
        assertEquals("string", schema.get("properties").get("title").get("type").asString());
        assertEquals("string", schema.get("properties").get("summary").get("type").asString());
        assertEquals(2, schema.get("required").size());
        assertEquals("title", schema.get("required").get(0).asString());
        assertFalse(schema.get("additionalProperties").asBoolean());
    }
}
