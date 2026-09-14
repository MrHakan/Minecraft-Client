package me.mrhakan.agalarhack.ui;

import java.util.List;
import java.util.Locale;
import me.mrhakan.agalarhack.ui.hud.ModuleListModel;
import me.mrhakan.agalarhack.ui.hud.ModuleListModel.Entry;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ModuleListModelTest {
    private final List<Entry> entries = List.of(new Entry("Speed", "Speed [1.25x]", "Movement"),
            new Entry("Aura", "Aura [Closest]", "Combat"), new Entry("ESP", "ESP", "Render"));

    @Test void sortsByVisibleWidthAndBoundsRows() {
        var rows = ModuleListModel.rows(entries, "Display", "Normal", "Width", 2, String::length);
        assertEquals(List.of("Aura", "Speed"), rows.stream().map(ModuleListModel.Row::id).toList());
        assertEquals("Aura [Closest]", rows.getFirst().text());
        assertEquals(14, rows.getFirst().width());
        assertTrue(ModuleListModel.rows(entries, "Name", "Normal", "Width", 0, String::length).isEmpty());
    }

    @Test void categoryAndNameOrdersAreStableAtEqualWidths() {
        var rows = ModuleListModel.rows(entries, "Category", "Normal", "Category", 64, String::length);
        assertEquals("Aura [Combat]", rows.getFirst().text());
        var ties = List.of(new Entry("Zed", "xx", "Render"), new Entry("Able", "xx", "Render"));
        assertEquals("Able", ModuleListModel.rows(ties,"Display","Normal","Width",64,String::length).getFirst().id());
        assertEquals(List.of("Aura","ESP","Speed"), ModuleListModel.rows(entries,"Name","Normal","Name",64,String::length)
                .stream().map(ModuleListModel.Row::id).toList());
    }

    @Test void formattingDoesNotDependOnSystemLocaleAndBoundsCustomNames() {
        Locale old = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            var rows = ModuleListModel.rows(List.of(new Entry("Flight","i".repeat(300),"Movement")),
                    "Display","Upper","Name",64,String::length);
            assertEquals("I".repeat(128), rows.getFirst().text());
        } finally { Locale.setDefault(old); }
    }
}
