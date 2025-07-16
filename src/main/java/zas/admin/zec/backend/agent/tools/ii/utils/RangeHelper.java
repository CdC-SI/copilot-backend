package zas.admin.zec.backend.agent.tools.ii.utils;

import zas.admin.zec.backend.agent.tools.ii.model.Index;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public final class RangeHelper {

    private RangeHelper() {}

    public static String matchingIdForTarget(String id, Index index) {
        if ("01-96".equals(id) || "05-96".equals(id)) {
            return index.totalId();
        }

        Map<String, List<Integer>> ranges = rangeByIdsForTarget(index);
        if (ranges.containsKey(id)) {
            return id;
        }

        List<Integer> currentRange = toRange(id);
        return ranges.entrySet()
                .stream()
                .filter(entry -> {
                    List<Integer> entryRange = entry.getValue();
                    return entryRange.size() >= currentRange.size() && new HashSet<>(entryRange).containsAll(currentRange);
                })
                .sorted(Comparator.comparingInt(range -> range.getValue().size()))
                .limit(1)
                .map(Map.Entry::getKey)
                .findFirst()
                .orElseThrow();
    }

    public static Map<String, List<Integer>> rangeByIdsForTarget(Index index) {
        return index.ids()
                .stream()
                .collect(Collectors.toMap(
                        Function.identity(),
                        RangeHelper::toRange));
    }

    public static List<Integer> toRange(String id) {
        if (id.contains("+")) {
            // only this range matches
            return List.of(77, 79, 80, 81, 82);
        }

        if (id.contains("/")) {
            if (id.contains("-")) {
                // e.g. "05-09/35-39"
                return Arrays.stream(id.split("/"))
                        .flatMap(r -> parseDashedRange(r).stream())
                        .toList();
            } else {
                // e.g. "64/66"
                return Arrays.stream(id.split("/"))
                        .map(Integer::parseInt)
                        .toList();
            }
        }

        if (id.contains("-")) {
            // e.g. "05-43"
            return parseDashedRange(id);
        }

        // single value
        return List.of(Integer.parseInt(id));
    }

    private static List<Integer> parseDashedRange(String r) {
        String[] parts = r.split("-");
        int start = Integer.parseInt(parts[0]);
        int end = Integer.parseInt(parts[parts.length - 1]);
        return IntStream.rangeClosed(start, end)
                .boxed()
                .toList();
    }
}
