package zas.admin.zec.backend.agent.tools.ii.utils;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import zas.admin.zec.backend.agent.tools.ii.model.DataPoint;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Year;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class CsvExporter {

    private CsvExporter() {}

    /** Pivoté : 1 ligne = id, 1 colonne par année */
    public static void writePivoted(Path out,
                                    List<DataPoint> rows,
                                    Comparator<Year> columnSorter) throws IOException {

        // 1) collecter l’ensemble des années présentes
        Set<Year> years = rows.stream()
                .flatMap(dp -> dp.indexValues().keySet().stream())
                .map(Year::parse)          // "2011" → Year
                .collect(Collectors.toCollection(TreeSet::new));

        // 2) construire le CSVFormat avec en-tête dynamique
        CSVFormat format = CSVFormat.Builder.create()
                .setHeader(Stream.concat(Stream.of("id"),
                                years.stream().sorted(columnSorter)
                                        .map(Year::toString))
                        .toArray(String[]::new))
                .build();

        try (var writer = Files.newBufferedWriter(out);
             var csv = new CSVPrinter(writer, format)) {

            for (DataPoint dp : rows) {
                List<String> record = new ArrayList<>();
                record.add(dp.id());

                for (Year y : years) {
                    record.add(Optional.ofNullable(dp.indexValues().get(y.toString()))
                            .map(Object::toString)
                            .orElse(""));
                }
                csv.printRecord(record);
            }
        }
    }

    /** Map id → label */
    public static void writeBranches(Path out, Map<String,String> idToLabel) throws IOException {

        CSVFormat format = CSVFormat.Builder.create()
                .setHeader("id", "label")
                .build();

        try (var writer = Files.newBufferedWriter(out);
             var csv = new CSVPrinter(writer, format)) {

            idToLabel.forEach((id,label) -> {
                try { csv.printRecord(id, label); }
                catch (IOException e) { throw new UncheckedIOException(e); }
            });
        }
    }
}
