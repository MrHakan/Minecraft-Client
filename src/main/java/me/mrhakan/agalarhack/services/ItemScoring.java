package me.mrhakan.agalarhack.services;

/**
 * Pure comparison logic for armour and weapons.
 *
 * <p>The scores are deliberately expressed over plain numbers rather than {@code ItemStack} so the
 * ranking rules can be unit tested without a Minecraft runtime. {@link InventoryService} adapts real
 * stacks into these records using the attribute/enchantment components read from the live item.
 *
 * <p>Scores are only ever compared against other scores produced by the same method. They are not
 * damage predictions and must never be presented to the user as server-authoritative values.
 */
public final class ItemScoring {
    private ItemScoring() { }

    /** A candidate armour piece. {@code durabilityFraction} is remaining/max, 1.0 for indestructible items. */
    public record ArmorStats(double armor, double toughness, double knockbackResistance,
                             int protection, int projectileProtection, int blastProtection, int fireProtection,
                             int thorns, int unbreaking, double durabilityFraction, boolean damageable,
                             boolean custom) {
        public ArmorStats {
            armor = finite(armor); toughness = finite(toughness); knockbackResistance = finite(knockbackResistance);
            durabilityFraction = clampFraction(durabilityFraction);
            protection = clampLevel(protection); projectileProtection = clampLevel(projectileProtection);
            blastProtection = clampLevel(blastProtection); fireProtection = clampLevel(fireProtection);
            thorns = clampLevel(thorns); unbreaking = clampLevel(unbreaking);
        }
    }

    /** A candidate weapon. Speed is the resulting attacks-per-second, not the raw attribute modifier. */
    public record WeaponStats(double attackDamage, double attackSpeed,
                              int sharpness, int smite, int baneOfArthropods, int fireAspect, int knockback,
                              double durabilityFraction, boolean damageable, boolean custom) {
        public WeaponStats {
            attackDamage = finite(attackDamage); attackSpeed = Math.max(0.05, finite(attackSpeed));
            durabilityFraction = clampFraction(durabilityFraction);
            sharpness = clampLevel(sharpness); smite = clampLevel(smite);
            baneOfArthropods = clampLevel(baneOfArthropods); fireAspect = clampLevel(fireAspect);
            knockback = clampLevel(knockback);
        }
    }

    /** Which vanilla enchantment family applies extra damage to the intended target. */
    public enum TargetFamily { GENERIC, UNDEAD, ARTHROPOD }

    private static double finite(double value) { return Double.isFinite(value) ? value : 0; }
    private static double clampFraction(double value) {
        return Double.isFinite(value) ? Math.max(0, Math.min(1, value)) : 1;
    }
    private static int clampLevel(int level) { return Math.max(0, Math.min(255, level)); }

    /**
     * Higher is better. Protection enchantments are weighted using vanilla's damage-reduction
     * behaviour: generic Protection applies to every source, the specialised variants only to part of it.
     */
    public static double armorScore(ArmorStats stats) {
        if (stats == null) return Double.NEGATIVE_INFINITY;
        double score = stats.armor()
                + stats.toughness() * 1.5
                + stats.knockbackResistance() * 8
                + stats.protection() * 1.25
                + (stats.projectileProtection() + stats.blastProtection() + stats.fireProtection()) * 0.35
                + stats.thorns() * 0.2
                + stats.unbreaking() * 0.1;
        return score * durabilityFactor(stats.durabilityFraction(), stats.damageable());
    }

    /**
     * Higher is better. {@code speedWeight} blends raw per-hit damage (0) with sustained
     * damage per second (1) so axes and swords can both be preferred deliberately.
     */
    public static double weaponScore(WeaponStats stats, TargetFamily family, double speedWeight) {
        if (stats == null) return Double.NEGATIVE_INFINITY;
        double weight = Double.isFinite(speedWeight) ? Math.max(0, Math.min(1, speedWeight)) : 0;
        double damage = stats.attackDamage() + enchantmentDamage(stats, family) + stats.fireAspect() * 0.5;
        double perHit = Math.max(0, damage);
        double perSecond = perHit * stats.attackSpeed();
        double score = perHit * (1 - weight) + perSecond * weight + stats.knockback() * 0.15;
        return score * durabilityFactor(stats.durabilityFraction(), stats.damageable());
    }

    /** Vanilla bonus damage: Sharpness adds 0.5 per level above the first, Smite/Bane add 2.5 per level. */
    public static double enchantmentDamage(WeaponStats stats, TargetFamily family) {
        if (stats == null) return 0;
        double sharpness = stats.sharpness() > 0 ? 0.5 * stats.sharpness() + 0.5 : 0;
        return switch (family == null ? TargetFamily.GENERIC : family) {
            case UNDEAD -> Math.max(sharpness, stats.smite() * 2.5);
            case ARTHROPOD -> Math.max(sharpness, stats.baneOfArthropods() * 2.5);
            case GENERIC -> sharpness;
        };
    }

    /**
     * Nearly broken gear is progressively devalued instead of being hard-rejected, so a damaged
     * diamond chestplate still beats an undamaged leather one.
     */
    public static double durabilityFactor(double durabilityFraction, boolean damageable) {
        if (!damageable) return 1;
        double fraction = clampFraction(durabilityFraction);
        if (fraction >= 0.25) return 1;
        return 0.4 + (fraction / 0.25) * 0.6;
    }

    /**
     * Whether {@code candidate} is worth a swap. {@code minimumImprovement} is an absolute score
     * delta so automation does not thrash between two nearly identical pieces.
     */
    public static boolean isUpgrade(double candidate, double current, double minimumImprovement) {
        if (!Double.isFinite(candidate)) return false;
        double baseline = Double.isFinite(current) ? current : Double.NEGATIVE_INFINITY;
        double threshold = Double.isFinite(minimumImprovement) ? Math.max(0, minimumImprovement) : 0;
        if (baseline == Double.NEGATIVE_INFINITY) return candidate > 0;
        return candidate - baseline > threshold;
    }
}
