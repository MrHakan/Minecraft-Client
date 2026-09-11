package me.mrhakan.agalarhack.ui.hud;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.ToIntFunction;

/** Pure formatting/order; callers supply only enabled, visible modules. */
public final class ModuleListModel {
    public record Entry(String id, String display, String category) { }
    public record Row(String id, String text, String category, int width) { }
    private ModuleListModel() { }

    public static List<Row> rows(List<Entry> entries, String displayMode, String letterCase,
                                  String sorting, int maximum, ToIntFunction<String> measure) {
        Comparator<Row> byName = Comparator.comparing(Row::id, String.CASE_INSENSITIVE_ORDER).thenComparing(Row::id);
        Comparator<Row> order = switch (sorting) {
            case "Name" -> byName;
            case "Category" -> Comparator.comparing(Row::category).thenComparing(byName);
            default -> Comparator.comparingInt(Row::width).reversed().thenComparing(byName);
        };
        return entries.stream().limit(1024).map(entry -> {
            String text = switch (displayMode) {
                case "Name" -> entry.id();
                case "Category" -> entry.id() + " [" + entry.category() + "]";
                default -> entry.display();
            };
            text = switch (letterCase) {
                case "Upper" -> text.toUpperCase(Locale.ROOT);
                case "Lower" -> text.toLowerCase(Locale.ROOT);
                default -> text;
            };
            if (text.length() > 128) text = text.substring(0, 128);
            return new Row(entry.id(), text, entry.category(), Math.max(0, measure.applyAsInt(text)));
        }).sorted(order).limit(Math.clamp(maximum, 0, 64)).toList();
    }
}
