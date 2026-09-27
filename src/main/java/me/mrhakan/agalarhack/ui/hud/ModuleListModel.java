package me.mrhakan.agalarhack.ui.hud;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.ToIntFunction;

/** Pure formatting/order; callers supply only enabled, visible modules. */
public final class ModuleListModel {
    private static final String OVERFLOW_ID = "__module_list_overflow__";

    public record Entry(String id, String display, String category) { }
    public record Row(String id, String text, String category, int width) { }
    private ModuleListModel() { }

    public static List<Row> rows(List<Entry> entries, String displayMode, String letterCase,
                                  String sorting, int maximum, ToIntFunction<String> measure) {
        return rows(entries, displayMode, letterCase, sorting, maximum, measure, false);
    }

    /**
     * Builds the settled rows for the HUD.
     *
     * <p>The optional overflow row reserves the final slot only when at least two rows fit. A
     * one-row HUD should still show an actual module rather than replacing the entire list with
     * metadata.
     */
    public static List<Row> rows(List<Entry> entries, String displayMode, String letterCase,
                                  String sorting, int maximum, ToIntFunction<String> measure,
                                  boolean overflowIndicator) {
        int limit = Math.clamp(maximum, 0, 64);
        if (limit == 0 || entries == null || entries.isEmpty()) return List.of();

        Comparator<Row> byName = Comparator.comparing(Row::id, String.CASE_INSENSITIVE_ORDER).thenComparing(Row::id);
        Comparator<Row> order = switch (sorting) {
            case "Name" -> byName;
            case "Category" -> Comparator.comparing(Row::category).thenComparing(byName);
            default -> Comparator.comparingInt(Row::width).reversed().thenComparing(byName);
        };

        int inputLimit = Math.min(1024, entries.size());
        List<Row> prepared = new ArrayList<>(inputLimit);
        for (int index = 0; index < inputLimit; index++) {
            Entry entry = entries.get(index);
            if (entry == null) continue;
            String id = entry.id() == null ? "" : entry.id();
            String category = entry.category() == null ? "" : entry.category();
            String text = switch (displayMode) {
                case "Name" -> id;
                case "Category" -> id + " [" + category + "]";
                default -> entry.display() == null ? id : entry.display();
            };
            text = switch (letterCase) {
                case "Upper" -> text.toUpperCase(Locale.ROOT);
                case "Lower" -> text.toLowerCase(Locale.ROOT);
                default -> text;
            };
            if (text.length() > 128) text = text.substring(0, 128);
            prepared.add(new Row(id, text, category, Math.max(0, measure.applyAsInt(text))));
        }
        prepared.sort(order);

        if (prepared.size() <= limit) return List.copyOf(prepared);
        if (!overflowIndicator || limit < 2) return List.copyOf(prepared.subList(0, limit));

        int visibleModules = limit - 1;
        int hidden = prepared.size() - visibleModules;
        List<Row> visible = new ArrayList<>(limit);
        visible.addAll(prepared.subList(0, visibleModules));
        String label = "+" + hidden + " more";
        visible.add(new Row(OVERFLOW_ID, label, "", Math.max(0, measure.applyAsInt(label))));
        return List.copyOf(visible);
    }

    public static boolean isOverflow(Row row) {
        return row != null && OVERFLOW_ID.equals(row.id());
    }
}
