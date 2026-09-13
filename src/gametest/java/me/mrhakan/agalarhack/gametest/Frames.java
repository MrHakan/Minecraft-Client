package me.mrhakan.agalarhack.gametest;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

/**
 * Compares two frames the test took itself.
 *
 * <p>Render modules hold no state worth asserting on - an ESP draws a box and forgets it - so the
 * only evidence they work is the picture. Reference images are the usual way to do that and a bad
 * fit here: the frame depends on which software rasteriser drew it, so an image captured on this
 * machine says little about the one CI produces.
 *
 * <p>Nothing here is compared against a stored image. A scenario takes two frames with the module
 * off to find out how much two consecutive frames differ on their own, then one with it on, and asks
 * whether the module changed the picture by more than that. All three come from the same run on the
 * same machine, so the renderer cancels out, and the noise measurement is the control: if two
 * identical-conditions frames differ as much as on-versus-off does, the scenario has proven nothing
 * and says so.
 */
final class Frames {
    private Frames() { }

    /**
     * Mean absolute difference per channel, 0-255, over the middle of the frame.
     *
     * <p>Cropped because the corners hold the HUD. A module that only changed a HUD line would
     * otherwise look exactly like one that drew in the world.
     */
    static double difference(Path first, Path second) {
        return difference(first, second, true);
    }

    static double difference(Path first, Path second, boolean crop) {
        BufferedImage a = read(first);
        BufferedImage b = read(second);
        if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight()) {
            throw new AssertionError("frames differ in size (" + a.getWidth() + "x" + a.getHeight()
                    + " against " + b.getWidth() + "x" + b.getHeight() + "); the window resized "
                    + "mid-scenario and the comparison is meaningless");
        }
        int left = crop ? a.getWidth() / 4 : 0;
        int right = a.getWidth() - left;
        int top = crop ? a.getHeight() / 4 : 0;
        int bottom = a.getHeight() - top;

        long total = 0;
        long samples = 0;
        for (int y = top; y < bottom; y++) {
            for (int x = left; x < right; x++) {
                int p = a.getRGB(x, y);
                int q = b.getRGB(x, y);
                total += Math.abs(((p >> 16) & 0xFF) - ((q >> 16) & 0xFF));
                total += Math.abs(((p >> 8) & 0xFF) - ((q >> 8) & 0xFF));
                total += Math.abs((p & 0xFF) - (q & 0xFF));
                samples += 3;
            }
        }
        return samples == 0 ? 0 : (double) total / samples;
    }

    /**
     * How many pixels differ noticeably, within a band of the frame given as fractions of its height.
     *
     * <p>Counting pixels rather than averaging: a mean over the whole crop makes a one-pixel line
     * across the frame look like 0.10 and a small label look like 0.01, while both are plainly
     * drawings. And a band, because where a module draws is known in advance - a nametag sits above
     * the entity, so measuring only that strip excludes the animating body underneath it instead of
     * letting the body's noise decide the answer.
     */
    static int changedPixels(Path first, Path second, double fromHeight, double toHeight) {
        // The middle half horizontally: what a module draws in front of the player, away from the
        // vanilla HUD at the edges.
        return changedPixels(first, second, fromHeight, toHeight, 0.25, 0.75);
    }

    /**
     * As above, over an explicit horizontal band.
     *
     * <p>The mod's own HUD sits at the screen's corners, which the default crop is chosen to exclude;
     * measuring it needs the full width.
     */
    static int changedPixels(Path first, Path second, double fromHeight, double toHeight,
            double fromWidth, double toWidth) {
        BufferedImage a = read(first);
        BufferedImage b = read(second);
        if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight()) {
            throw new AssertionError("frames differ in size; the window resized mid-scenario");
        }
        int left = (int) Math.round(a.getWidth() * fromWidth);
        int right = (int) Math.round(a.getWidth() * toWidth);
        int top = (int) Math.round(a.getHeight() * fromHeight);
        int bottom = (int) Math.round(a.getHeight() * toHeight);
        int changed = 0;
        for (int y = top; y < bottom; y++) {
            for (int x = left; x < right; x++) {
                int p = a.getRGB(x, y);
                int q = b.getRGB(x, y);
                // Eight levels per channel: above rounding between two renders of the same scene,
                // far below anything a module deliberately draws.
                if (Math.abs(((p >> 16) & 0xFF) - ((q >> 16) & 0xFF)) > 8
                        || Math.abs(((p >> 8) & 0xFF) - ((q >> 8) & 0xFF)) > 8
                        || Math.abs((p & 0xFF) - (q & 0xFF)) > 8) {
                    changed++;
                }
            }
        }
        return changed;
    }

    private static BufferedImage read(Path path) {
        try {
            if (!Files.isRegularFile(path)) {
                throw new AssertionError("no screenshot at " + path);
            }
            BufferedImage image = ImageIO.read(path.toFile());
            if (image == null) {
                throw new AssertionError("could not decode the screenshot at " + path);
            }
            return image;
        } catch (IOException unreadable) {
            throw new UncheckedIOException("Could not read " + path, unreadable);
        }
    }
}
