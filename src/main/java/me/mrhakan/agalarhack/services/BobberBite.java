package me.mrhakan.agalarhack.services;

/**
 * Infers a bite from the bobber's visible downward motion.
 *
 * <p>The client is never told there is a fish. {@code FishingHook.nibble} and its state machine are
 * server-side and private, and the client's copy of the entity does not carry them. What the client
 * does see is the bobber being pulled under — the same cue a player watches for — so that is what
 * this reads, and why the module describes its trigger as the bobber's motion rather than as a fish.
 *
 * <p>The minimum gap between detections is not cosmetic. A bite is not one tick of downward motion
 * but a plunge lasting several, so without it a single bite reports repeatedly and the module reels
 * in, recasts, and reels the empty line straight back.
 *
 * <p>Kept free of Minecraft types so the threshold and the gap are unit tested directly.
 */
public final class BobberBite {
    private BobberBite() { }

    /** Vanilla yanks the bobber down hard; ordinary bobbing is far gentler than this. */
    public static final double DEFAULT_THRESHOLD = 0.08;

    public static final class Detector {
        private final double threshold;
        private final int minimumGapTicks;
        private long lastDetection = Long.MIN_VALUE;

        /**
         * @param threshold downward speed, in blocks per tick, that counts as being pulled under
         * @param minimumGapTicks how long one plunge is suppressed for after it is first seen
         */
        public Detector(double threshold, int minimumGapTicks) {
            this.threshold = Math.max(0.001, threshold);
            this.minimumGapTicks = Math.max(1, minimumGapTicks);
        }

        /**
         * Call once per tick with the bobber's current state.
         *
         * @param inWater whether the bobber is floating; a bobber still flying through the air is
         *                moving downward under gravity, which is not a bite
         * @param motionY vertical velocity, negative downward
         * @return true on the tick a plunge is first recognised
         */
        public boolean update(long tick, boolean inWater, double motionY) {
            if (!inWater || !Double.isFinite(motionY) || motionY > -threshold) return false;
            if (lastDetection != Long.MIN_VALUE && tick - lastDetection < minimumGapTicks) return false;
            lastDetection = tick;
            return true;
        }

        /** The line was reeled in or the hook is gone; the next cast starts from nothing. */
        public void reset() {
            lastDetection = Long.MIN_VALUE;
        }
    }
}
