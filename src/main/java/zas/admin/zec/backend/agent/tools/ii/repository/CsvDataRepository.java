package zas.admin.zec.backend.agent.tools.ii.repository;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;
import zas.admin.zec.backend.agent.tools.ii.model.DataPoint;
import zas.admin.zec.backend.agent.tools.ii.model.TableKind;
import zas.admin.zec.backend.config.properties.AIAgentProperties;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

@Component
public final class CsvDataRepository implements DataRepository {

    private final Path baseDir;
    private final Map<String,String> branches = new HashMap<>();
    private final Map<TableKind, List<DataPoint>> cache = new EnumMap<>(TableKind.class);

    public CsvDataRepository(AIAgentProperties properties) throws IOException {
        this.baseDir = Path.of(properties.iiDataFolder());
        loadBranches();
    }

    // --------------------------------------------------------------------
    // public API ----------------------------------------------------------
    // --------------------------------------------------------------------

    /**
     * Retourne l'ensemble des labels (id→label).
     */
    @Override
    public Map<String, String> loadLabels() {
        return Collections.unmodifiableMap(branches);
    }

    /**
     * Renvoie l'ensemble des tables déjà chargées.
     */
    @Override
    public Map<String, List<DataPoint>> loadTables() {
        Arrays.stream(TableKind.values()).forEach(this::ensureLoaded);
        return cache.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(e -> e.getKey().name(), Map.Entry::getValue));
    }

    private void loadBranches() throws IOException {
        Path csv = baseDir.resolve("branches.csv");
        try (Reader r = Files.newBufferedReader(csv);
             CSVParser p = CSVFormat.Builder.create().setHeader().setSkipHeaderRecord(true).build().parse(r)) {
            for (CSVRecord rec : p) {
                branches.put(rec.get("id"), rec.get("label"));
            }
        }
    }

    private void ensureLoaded(TableKind kind) {
        cache.computeIfAbsent(kind, k -> {
            try {
                switch (k) {
                    case TA1 -> { return loadTa1(); }
                    default   -> { return loadPivoted(k.file()); }
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    private List<DataPoint> loadPivoted(String fileName) throws IOException {
        Path csv = baseDir.resolve(fileName);
        try (Reader r = Files.newBufferedReader(csv);
             CSVParser p = CSVFormat.Builder.create().setHeader().setSkipHeaderRecord(true).build().parse(r)) {

            List<String> years = p.getHeaderNames().stream()
                    .filter(h -> !"id".equals(h))
                    .toList();

            List<DataPoint> list = new ArrayList<>();
            for (CSVRecord rec : p) {
                String id = rec.get("id");
                Map<String, Double> values = new HashMap<>();
                for (String y : years) {
                    String cell = rec.get(y).trim();
                    if (!cell.isBlank()) {
                        values.put(y, Double.parseDouble(cell));
                    }
                }
                list.add(new DataPoint(id, branches.getOrDefault(id, id), values));
            }
            return list;
        }
    }

    private List<DataPoint> loadTa1() throws IOException {
        Path csv = baseDir.resolve(TableKind.TA1.file());
        try (Reader r = Files.newBufferedReader(csv);
             CSVParser p = CSVFormat.Builder.create().setHeader().setSkipHeaderRecord(true).build().parse(r)) {

            List<String> keys = p.getHeaderNames().stream()
                    .filter(h -> !List.of("id","label").contains(h))
                    .toList();

            List<DataPoint> list = new ArrayList<>();
            for (CSVRecord rec : p) {
                String id    = rec.get("id");
                String label = rec.get("label");
                Map<String,Double> vals = new HashMap<>();
                for (String k : keys) {
                    String cell = rec.get(k).trim();
                    if (!cell.isBlank()) {
                        vals.put(k, Double.parseDouble(cell));
                    }
                }
                list.add(new DataPoint(id, label, vals));
            }
            return list;
        }
    }
}

