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
