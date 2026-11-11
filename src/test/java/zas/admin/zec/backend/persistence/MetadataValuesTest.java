package zas.admin.zec.backend.persistence;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MetadataValuesTest {

    @Test
    void stringIsReturnedAsIs() {
        assertEquals("fr", MetadataValues.getString(Map.of("language", "fr"), "language"));
    }

    @Test
    void numberAndBooleanAreConvertedWithStringValueOf() {
        Map<String, Object> metadata = Map.of("page_number", 3, "ratio", 1.5, "public", true);

        assertEquals("3", MetadataValues.getString(metadata, "page_number"));
        assertEquals("1.5", MetadataValues.getString(metadata, "ratio"));
        assertEquals("true", MetadataValues.getString(metadata, "public"));
    }

    @Test
    void arrayElementsAreJoinedWithComma() {
        Map<String, Object> metadata = Map.of("tags", List.of("AVS", "AI"), "pages", List.of(1, 2), "empty", List.of());

        assertEquals("AVS,AI", MetadataValues.getString(metadata, "tags"));
        assertEquals("1,2", MetadataValues.getString(metadata, "pages"));
        assertEquals("", MetadataValues.getString(metadata, "empty", "default"));
    }

    @Test
    void nullElementsInArrayAreIgnored() {
        assertEquals("AVS,AI", MetadataValues.getString(Map.of("tags", Arrays.asList("AVS", null, "AI")), "tags"));
    }

    @Test
    void missingOrNullValueReturnsDefault() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("url", null);

        assertNull(MetadataValues.getString(metadata, "title"));
        assertNull(MetadataValues.getString(metadata, "url"));
        assertEquals("", MetadataValues.getString(metadata, "title", ""));
        assertEquals("", MetadataValues.getString(metadata, "url", ""));
        assertEquals("", MetadataValues.getString(null, "url", ""));
    }
}
