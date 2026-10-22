package zas.admin.zec.backend.actions.catalog;

import java.util.List;

/**
 * Rapport d'import d'un catalogue : noms des éléments créés, mis à jour ou inchangés.
 */
public record CatalogImportReport(ItemReport sources, ItemReport workspaces) {

    public record ItemReport(List<String> created, List<String> updated, List<String> unchanged) {
        public ItemReport {
            created = List.copyOf(created);
            updated = List.copyOf(updated);
            unchanged = List.copyOf(unchanged);
        }
    }
}
