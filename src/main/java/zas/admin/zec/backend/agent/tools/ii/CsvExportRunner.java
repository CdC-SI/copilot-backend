package zas.admin.zec.backend.agent.tools.ii;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import zas.admin.zec.backend.agent.tools.ii.model.DataPoint;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static zas.admin.zec.backend.agent.tools.ii.StatisticalData.*;

public final class CsvExportRunner {

    public static void main(String[] args) throws Exception {

        Path targetDir = Path.of("export");
        Files.createDirectories(targetDir);

        // 1) master des branches (id, label)
        CsvExporter.writeBranches(
                targetDir.resolve("branches.csv"),
                loadLabelsId());

        // 2) tableaux T1 pivotés
        var t1 = loadT1();
        CsvExporter.writePivoted(
                targetDir.resolve("T1_male.csv"),
                t1.get("homme"),
                Comparator.naturalOrder());

        CsvExporter.writePivoted(
                targetDir.resolve("T1_female.csv"),
                t1.get("femme"),
                Comparator.naturalOrder());

        CsvExporter.writePivoted(
                targetDir.resolve("T1_rai.csv"),
                t1.get("26 al. 6 RAI"),
                Comparator.naturalOrder());

        // 3) tableau TA1 (non pivoté)
        writeTa1(targetDir.resolve("TA1.csv"));

        // 4) tableau TH (heures hebdo)
        CsvExporter.writePivoted(
                targetDir.resolve("TH.csv"),
                loadTh(),
                Comparator.naturalOrder());

        System.out.println("✅  Tous les CSV ont été générés dans " + targetDir.toAbsolutePath());
    }

    /** Export dédié pour TA1 : une colonne par clé (“total”, “4 total”, …). */
    private static void writeTa1(Path out) throws IOException {

        List<DataPoint> ta1 = loadTa1();

        // a) construire dynamiquement l’en-tête
        List<String> header = new ArrayList<>();
        header.add("id");
        header.add("label");
        header.addAll(ta1.getFirst().indexValues().keySet());   // total, 4 total, …

        CSVFormat format = CSVFormat.Builder.create()
                .setHeader(header.toArray(String[]::new))
                .build();

        // b) écrire
        try (var writer = Files.newBufferedWriter(out);
             var csv = new CSVPrinter(writer, format)) {

            for (DataPoint dp : ta1) {
                List<String> line = new ArrayList<>();
                line.add(dp.id());
                line.add(dp.label());
                for (String k : dp.indexValues().keySet()) {
                    line.add(dp.indexValues().get(k).toString());
                }
                csv.printRecord(line);
            }
        }
    }
}