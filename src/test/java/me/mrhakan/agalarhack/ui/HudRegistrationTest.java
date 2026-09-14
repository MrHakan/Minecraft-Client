package me.mrhakan.agalarhack.ui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Guards the startup crash this file actually caused.
 *
 * <p>{@code Hud.register} used to copy whatever {@code HUD_LAYOUT.get(id)} returned. For an id with
 * no declared default that was null, and the dereference happened during client initialisation —
 * before any screen exists to report it, so the whole client refused to start over a widget nobody
 * had given a position to.
 *
 * <p>{@code HudLayoutManager.get} is total now, which is the real fix. This is the second line of
 * defence: it fails if that call site starts dereferencing the lookup again without a guard, because
 * the consequence is disproportionate to how ordinary the mistake looks.
 */
class HudRegistrationTest {
    private static final Path HUD = Path.of("src/main/java/me/mrhakan/agalarhack/ui/Hud.java");
    private static final Path LAYOUT =
            Path.of("src/main/java/me/mrhakan/agalarhack/managers/HudLayoutManager.java");

    @Test
    void theLayoutLookupIsTotal() throws IOException {
        String source = Files.readString(LAYOUT);
        Matcher body = Pattern.compile("public WidgetState get\\(String id\\) \\{(.*?)\\n    \\}", Pattern.DOTALL)
                .matcher(source);
        assertTrue(body.find(), "HudLayoutManager.get is gone or was re-signatured");
        String text = body.group(1);
        assertTrue(text.contains("computeIfAbsent"),
                "get must supply a default rather than returning null: " + text.trim());
    }

    @Test
    void theWidgetsReadByNameStillHaveDeclaredDefaults() throws IOException {
        // Hud dereferences a few widgets by literal id. get() is total now, so a missing default is
        // no longer fatal - but it would silently give them the generic placement instead of the
        // one chosen for them, which is a quieter bug than a crash and harder to notice.
        String constructor = Files.readString(LAYOUT);
        Matcher declared = Pattern.compile("for\\(String id : new String\\[\\]\\{([^}]*)\\}").matcher(constructor);
        assertTrue(declared.find(), "the declared-default list is gone or was reshaped");
        String list = declared.group(1);

        List<String> missing = new ArrayList<>();
        Matcher reads = Pattern.compile("HUD_LAYOUT\\.get\\(\"([^\"]+)\"\\)").matcher(Files.readString(HUD));
        while (reads.find()) {
            if (!list.contains('"' + reads.group(1) + '"')) missing.add(reads.group(1));
        }
        assertTrue(missing.isEmpty(),
                "read by name but given no declared default, so they get the generic placement: " + missing);
    }

    @Test
    void everyWidgetIdIsAValidLayoutKey() throws IOException {
        // validateSnapshot rejects ids outside this pattern, which would make the layout unsavable.
        List<String> bad = new ArrayList<>();
        Matcher ids = Pattern.compile("(?:register|textComponent)\\(\\s*\"([^\"]+)\"").matcher(Files.readString(HUD));
        int found = 0;
        while (ids.find()) {
            found++;
            if (!ids.group(1).matches("[a-z0-9_.:-]{1,64}")) bad.add(ids.group(1));
        }
        assertTrue(found > 5, "the scan found almost nothing, so it is not really checking: " + found);
        assertTrue(bad.isEmpty(), "these ids cannot be persisted: " + bad);
    }
}
