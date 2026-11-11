package zas.admin.zec.backend.tools.document;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JsonbFilterSqlBuilderTest {

    private final JsonbFilterSqlBuilder builder = new JsonbFilterSqlBuilder(
            List.of("id", "doc_id", "chunk_index", "title"), List.of("chunk_index"));

    @Test
    void buildsParameterizedConditionsCombinedWithAnd() {
        var fragment = builder.build(List.of(
                new MetadataFilterSpec("doc_id", FilterOp.EQ, "abc", null),
                new MetadataFilterSpec("chunk_index", FilterOp.IN, null, List.of("4", "6")),
                new MetadataFilterSpec("title", FilterOp.CONTAINS, "50%_off", null)));

        assertTrue(fragment.sql().startsWith("(metadata @> ?::jsonb OR metadata @> ?::jsonb) AND "));
        assertTrue(fragment.sql().contains("::numeric END) IN (?, ?)"));
        assertTrue(fragment.sql().endsWith("(metadata ->> 'title') ILIKE ? ESCAPE '\\'"));
        assertEquals(List.of("{\"doc_id\":\"abc\"}", "{\"doc_id\":[\"abc\"]}",
                new BigDecimal("4"), new BigDecimal("6"), "%50\\%\\_off%"), fragment.params());
    }

    @Test
    void equalityUsesContainmentForStringsArraysAndNumbers() {
        var fragment = builder.build(List.of(new MetadataFilterSpec("title", FilterOp.IN, null, List.of(" a ", "42"))));

        assertEquals("(metadata @> ?::jsonb OR metadata @> ?::jsonb OR metadata @> ?::jsonb OR metadata @> ?::jsonb"
                + " OR metadata @> ?::jsonb)", fragment.sql());
        assertEquals(List.of("{\"title\":\"a\"}", "{\"title\":[\"a\"]}",
                "{\"title\":\"42\"}", "{\"title\":[\"42\"]}", "{\"title\":42}"), fragment.params());
    }

    @Test
    void idTargetsPrimaryKeyAsUuid() {
        String uuid = "11111111-2222-3333-4444-555555555555";
        var eq = builder.build(List.of(new MetadataFilterSpec("id", FilterOp.EQ, " " + uuid + " ", null)));
        var in = builder.build(List.of(new MetadataFilterSpec("id", FilterOp.IN, null, List.of(uuid, uuid))));

        assertEquals("id = ?", eq.sql());
        assertEquals(List.of(UUID.fromString(uuid)), eq.params());
        assertEquals("id IN (?, ?)", in.sql());
    }

    @Test
    void idRejectsNonUuidValuesAndOtherOperators() {
        var e = assertThrows(JsonbFilterSqlBuilder.InvalidFilterException.class, () -> builder.build(List.of(
                new MetadataFilterSpec("id", FilterOp.EQ, "1-1-1-1-1", null))));
        assertTrue(e.getMessage().contains("UUID"));
        assertThrows(JsonbFilterSqlBuilder.InvalidFilterException.class, () -> builder.build(List.of(
                new MetadataFilterSpec("id", FilterOp.CONTAINS, "1111", null))));
    }

    @Test
    void comparesNumericKeysNumericallyAndOthersLexicographically() {
        var fragment = builder.build(List.of(
                new MetadataFilterSpec("chunk_index", FilterOp.GTE, "3", null),
                new MetadataFilterSpec("title", FilterOp.LTE, "m", null)));
        assertTrue(fragment.sql().contains("::numeric END) >= ?"));
        assertTrue(fragment.sql().endsWith("(metadata ->> 'title') <= ?"));
        assertEquals(List.of(new BigDecimal("3"), "m"), fragment.params());
    }

    @Test
    void rejectsUnknownKeysIncludingInjectionAttempts() {
        var e = assertThrows(JsonbFilterSqlBuilder.InvalidFilterException.class, () -> builder.build(List.of(
                new MetadataFilterSpec("title') OR 1=1 --", FilterOp.EQ, "x", null))));
        assertTrue(e.getMessage().contains("Allowed keys"));
        assertThrows(JsonbFilterSqlBuilder.InvalidFilterException.class, () -> builder.build(List.of(
                new MetadataFilterSpec("organizations", FilterOp.EQ, "ZAS", null))));
    }

    @Test
    void valuesAreNeverInlined() {
        var fragment = builder.build(List.of(new MetadataFilterSpec("title", FilterOp.EQ, "x' OR '1'='1", null)));
        assertFalse(fragment.sql().contains("OR '1'"));
        assertEquals(List.of("{\"title\":\"x' OR '1'='1\"}", "{\"title\":[\"x' OR '1'='1\"]}"), fragment.params());
    }

    @Test
    void rejectsInvalidFilters() {
        assertThrows(JsonbFilterSqlBuilder.InvalidFilterException.class, () -> builder.build(List.of()));
        assertThrows(JsonbFilterSqlBuilder.InvalidFilterException.class, () -> builder.build(null));
        assertThrows(JsonbFilterSqlBuilder.InvalidFilterException.class, () -> builder.build(List.of(
                new MetadataFilterSpec("title", FilterOp.EQ, " ", null))));
        assertThrows(JsonbFilterSqlBuilder.InvalidFilterException.class, () -> builder.build(List.of(
                new MetadataFilterSpec("title", FilterOp.IN, null, List.of()))));
        assertThrows(JsonbFilterSqlBuilder.InvalidFilterException.class, () -> builder.build(List.of(
                new MetadataFilterSpec("chunk_index", FilterOp.EQ, "abc", null))));
        assertThrows(JsonbFilterSqlBuilder.InvalidFilterException.class, () -> builder.build(List.of(
                new MetadataFilterSpec("title", null, "x", null))));
    }

    @Test
    void detectsFiltersOnKeys() {
        var filters = List.of(new MetadataFilterSpec("doc_id", FilterOp.EQ, "a", null));
        assertTrue(builder.hasFilterOn(filters, "doc_id", "id"));
        assertFalse(builder.hasFilterOn(filters, "title"));
    }
}
