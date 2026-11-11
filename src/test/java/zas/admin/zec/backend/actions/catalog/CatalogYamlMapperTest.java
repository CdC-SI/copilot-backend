package zas.admin.zec.backend.actions.catalog;

import jakarta.validation.Validation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CatalogYamlMapperTest {

    private final CatalogYamlMapper mapper =
            new CatalogYamlMapper(Validation.buildDefaultValidatorFactory().getValidator());

    private CatalogFile parse(String yaml) {
        return mapper.parse(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));
    }

    private List<String> errorsOf(String yaml) {
        return assertThrows(InvalidCatalogException.class, () -> parse(yaml)).getErrors();
    }

    @Test
    @DisplayName("parse lit un catalogue complet et applique les valeurs par défaut")
    void parse_validCatalog() {
        var catalog = parse("""
                version: 1
                sources:
                  - name: avs
                    description: Assurance-vieillesse
                    hypotheticalQuestions:
                      - yes
                      - 2024-01-01
                  - name: ai
                workspaces:
                  - name: general
                    sources: [avs, ai]
                """);

        assertEquals(1, catalog.version());
        assertEquals(2, catalog.sources().size());
        assertEquals(List.of("yes", "2024-01-01"), catalog.sources().getFirst().hypotheticalQuestions());
        assertNull(catalog.sources().get(1).description());
        assertEquals(List.of(), catalog.sources().get(1).hypotheticalQuestions());
        assertEquals(List.of("avs", "ai"), catalog.workspaces().getFirst().sources());
        assertEquals(List.of(), catalog.workspaces().getFirst().hypotheticalQuestions());
    }

    @Test
    @DisplayName("parse refuse une propriété inconnue")
    void parse_unknownProperty() {
        var errors = errorsOf("""
                version: 1
                sources:
                  - name: avs
                    hypotheticalQuestion: [oops]
                """);
        assertTrue(errors.getFirst().contains("hypotheticalQuestion"), errors.toString());
    }

    @Test
    @DisplayName("parse refuse un YAML malformé, vide ou non objet")
    void parse_malformed() {
        assertTrue(errorsOf("version: 1\nsources: [unclosed").getFirst().startsWith("YAML invalide"));
        assertEquals(1, errorsOf("").size());
        assertEquals(1, errorsOf("- a\n- b").size());
    }

    @Test
    @DisplayName("parse refuse les clés dupliquées")
    void parse_duplicateKeys() {
        assertTrue(errorsOf("version: 1\nversion: 1\nsources:\n  - name: a").getFirst().startsWith("YAML invalide"));
    }

    @Test
    @DisplayName("parse collecte toutes les violations de contraintes")
    void parse_constraintViolations() {
        var errors = errorsOf("""
                version: 2
                sources:
                  - description: sans nom
                workspaces:
                  - name: ""
                    sources: [""]
                """);
        assertEquals(4, errors.size(), errors.toString());
        assertTrue(errors.stream().anyMatch(e -> e.startsWith("sources[0].name")));
        assertTrue(errors.stream().anyMatch(e -> e.startsWith("workspaces[0].name")));
        assertTrue(errors.stream().anyMatch(e -> e.startsWith("workspaces[0].sources[0]")));
        assertTrue(errors.stream().anyMatch(e -> e.startsWith("version")));
    }

    @Test
    @DisplayName("parse refuse un fichier sans version ni contenu")
    void parse_missingVersionAndContent() {
        var errors = errorsOf("sources: []");
        assertEquals(2, errors.size(), errors.toString());
    }

    @Test
    @DisplayName("write produit un YAML relisible à l'identique")
    void write_roundTrip() {
        var catalog = new CatalogFile(1,
                List.of(new CatalogFile.SourceEntry("avs", "Ligne 1\nLigne 2", List.of("yes", "Question : ?")),
                        new CatalogFile.SourceEntry("ai", null, List.of())),
                List.of(new CatalogFile.WorkspaceEntry("general", "Général", List.of(), List.of("ai", "avs"))));

        var yaml = mapper.write(catalog);

        assertTrue(yaml.contains("description: |"), yaml);
        assertEquals(catalog, parse(yaml));
    }
}
