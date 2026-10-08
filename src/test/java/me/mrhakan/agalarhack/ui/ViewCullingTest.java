package me.mrhakan.agalarhack.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

class ViewCullingTest {
    /** Every overlay family; the render methods moved out of one file into these in 2.0.03. */
    private static final Path OVERLAYS = Path.of("src/main/java/me/mrhakan/agalarhack/ui/overlay");

    private static String overlaySources() throws IOException {
        StringBuilder all = new StringBuilder();
        try (var files = Files.list(OVERLAYS)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).sorted().toList()) {
                all.append(Files.readString(file)).append('\n');
            }
        }
        return all.toString();
    }

    /**
     * Line overlays are deliberately never culled: a tracer or a breadcrumb trail has its far end off
     * screen by design while the line itself crosses the view, so testing an endpoint would erase the
     * geometry that carries the information. This is easy to "fix" later by someone tidying up, so it
     * is written down as a test rather than only as a comment.
     */
    private static final List<String> MUST_NOT_CULL =
            List.of("renderTracers", "renderBreadcrumbs", "renderStorageTracers");

    @Test
    void withoutAFrustumNothingIsCulled() {
        ViewCulling culling = ViewCulling.of(null);
        assertTrue(culling.isPassThrough());
        assertTrue(culling.isVisible(new BlockPos(0, 0, 0)));
        assertTrue(culling.isVisible(new BlockPos(9_000_000, -2000, -9_000_000)));
        assertTrue(culling.isVisible(new AABB(-1, -1, -1, 1, 1, 1)));
        assertTrue(culling.isVisible(new AABB(1e7, 1e7, 1e7, 1e7 + 1, 1e7 + 1, 1e7 + 1)),
                "no frustum must mean no culling, never the reverse");
    }

    @Test
    void lineOverlaysAreNotCulled() throws IOException {
        String source = overlaySources();
        List<String> offenders = new ArrayList<>();
        for (String method : MUST_NOT_CULL) {
            String body = methodBody(source, method);
            if (body.contains("culling")) offenders.add(method);
        }
        assertTrue(offenders.isEmpty(),
                "these draw lines whose far end is meant to be off screen: " + offenders);
    }

    @Test
    void theBoxOverlaysAreCulled() throws IOException {
        String source = overlaySources();
        List<String> unculled = new ArrayList<>();
        for (String method : List.of("renderBlockEsp", "renderStorageEsp", "renderSpawns",
                "renderHoles", "renderItemEsp", "renderWaypoints")) {
            if (!methodBody(source, method).contains("culling.isVisible")) unculled.add(method);
        }
        assertTrue(unculled.isEmpty(), "these should skip off-screen geometry: " + unculled);
    }

    @Test
    void culledBoxesAreTestedInWorldSpace() throws IOException {
        // The frustum is prepared in world space, so a camera-relative box would test the wrong
        // place - and quietly, because the answer is still a plausible-looking boolean.
        String source = overlaySources();
        List<String> wrongSpace = new ArrayList<>();
        for (String line : source.lines().toList()) {
            if (!line.contains("culling.isVisible")) continue;
            if (line.contains("-camera.")) wrongSpace.add(line.trim());
        }
        assertTrue(wrongSpace.isEmpty(), "tested after the camera offset: " + wrongSpace);
    }

    @Test
    void theGuardWouldNoticeIfTheMethodsWereRenamed() throws IOException {
        String source = overlaySources();
        for (String method : MUST_NOT_CULL) {
            assertFalse(methodBody(source, method).isEmpty(), method + " no longer exists");
        }
    }

    /** From the declaration to the next one at the same indent; coarse, but enough to see a call. */
    /** From the declaration to the next class-level line, which is the method's own closing brace. */
    private static String methodBody(String source, String method) {
        int start = source.indexOf("static void " + method + "(");
        if (start < 0) return "";
        var end = java.util.regex.Pattern.compile("\n    \\S").matcher(source);
        return end.find(start) ? source.substring(start, end.start()) : source.substring(start);
    }
}
