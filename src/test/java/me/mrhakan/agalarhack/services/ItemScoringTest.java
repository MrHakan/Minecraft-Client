package me.mrhakan.agalarhack.services;

import me.mrhakan.agalarhack.services.ItemScoring.ArmorStats;
import me.mrhakan.agalarhack.services.ItemScoring.TargetFamily;
import me.mrhakan.agalarhack.services.ItemScoring.WeaponStats;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ItemScoringTest {
    private static ArmorStats armor(double points, double toughness, int protection, double durability) {
        return new ArmorStats(points, toughness, 0, protection, 0, 0, 0, 0, 0, durability, true, false);
    }
    private static WeaponStats weapon(double damage, double speed, int sharpness, double durability) {
        return new WeaponStats(damage, speed, sharpness, 0, 0, 0, 0, durability, true, false);
    }

    @Test void strongerArmorOutranksWeakerArmor() {
        assertTrue(ItemScoring.armorScore(armor(8, 2, 0, 1)) > ItemScoring.armorScore(armor(5, 0, 0, 1)));
    }

    @Test void protectionCanOvercomeARawArmorPointDeficit() {
        assertTrue(ItemScoring.armorScore(armor(5, 0, 4, 1)) > ItemScoring.armorScore(armor(8, 0, 0, 1)));
    }

    @Test void damagedGearIsDevaluedButNotDiscarded() {
        double healthy = ItemScoring.armorScore(armor(8, 2, 0, 1));
        double nearlyBroken = ItemScoring.armorScore(armor(8, 2, 0, 0.01));
        assertTrue(nearlyBroken < healthy);
        assertTrue(nearlyBroken > ItemScoring.armorScore(armor(1, 0, 0, 1)));
    }

    @Test void durabilityOnlyPenalisesDamageableItems() {
        assertEquals(1.0, ItemScoring.durabilityFactor(0.0, false));
        assertEquals(1.0, ItemScoring.durabilityFactor(0.5, true));
        assertTrue(ItemScoring.durabilityFactor(0.0, true) < 1.0);
    }

    @Test void speedWeightDecidesBetweenAxeAndSword() {
        WeaponStats axe = weapon(9, 1.0, 0, 1);
        WeaponStats sword = weapon(7, 1.6, 0, 1);
        assertTrue(ItemScoring.weaponScore(axe, TargetFamily.GENERIC, 0) > ItemScoring.weaponScore(sword, TargetFamily.GENERIC, 0));
        assertTrue(ItemScoring.weaponScore(sword, TargetFamily.GENERIC, 1) > ItemScoring.weaponScore(axe, TargetFamily.GENERIC, 1));
    }

    @Test void smiteOnlyBeatsSharpnessAgainstUndead() {
        WeaponStats smite = new WeaponStats(7, 1.6, 0, 5, 0, 0, 0, 1, true, false);
        WeaponStats sharp = weapon(7, 1.6, 5, 1);
        assertTrue(ItemScoring.weaponScore(smite, TargetFamily.UNDEAD, 0) > ItemScoring.weaponScore(sharp, TargetFamily.UNDEAD, 0));
        assertTrue(ItemScoring.weaponScore(sharp, TargetFamily.GENERIC, 0) > ItemScoring.weaponScore(smite, TargetFamily.GENERIC, 0));
    }

    @Test void sharpnessFollowsTheVanillaCurve() {
        assertEquals(0.0, ItemScoring.enchantmentDamage(weapon(7, 1.6, 0, 1), TargetFamily.GENERIC));
        assertEquals(1.0, ItemScoring.enchantmentDamage(weapon(7, 1.6, 1, 1), TargetFamily.GENERIC));
        assertEquals(3.0, ItemScoring.enchantmentDamage(weapon(7, 1.6, 5, 1), TargetFamily.GENERIC));
    }

    @Test void upgradeRequiresTheConfiguredMargin() {
        assertFalse(ItemScoring.isUpgrade(10.4, 10.0, 0.5));
        assertTrue(ItemScoring.isUpgrade(10.6, 10.0, 0.5));
        assertFalse(ItemScoring.isUpgrade(10.0, 10.0, 0));
    }

    @Test void emptyBaselineAcceptsAnyPositiveCandidate() {
        assertTrue(ItemScoring.isUpgrade(0.5, Double.NEGATIVE_INFINITY, 5));
        assertFalse(ItemScoring.isUpgrade(0, Double.NEGATIVE_INFINITY, 0));
    }

    @Test void nonFiniteInputsNeverProduceNonFiniteScores() {
        ArmorStats broken = new ArmorStats(Double.NaN, Double.POSITIVE_INFINITY, Double.NaN, -3, 0, 0, 0, 0, 0, Double.NaN, true, false);
        assertTrue(Double.isFinite(ItemScoring.armorScore(broken)));
        WeaponStats brokenWeapon = new WeaponStats(Double.NaN, Double.NaN, -1, 0, 0, 0, 0, Double.NaN, true, false);
        assertTrue(Double.isFinite(ItemScoring.weaponScore(brokenWeapon, null, Double.NaN)));
        assertEquals(Double.NEGATIVE_INFINITY, ItemScoring.armorScore(null));
        assertEquals(Double.NEGATIVE_INFINITY, ItemScoring.weaponScore(null, TargetFamily.GENERIC, 0));
    }

    @Test void enchantmentLevelsAreBounded() {
        WeaponStats absurd = new WeaponStats(7, 1.6, Integer.MAX_VALUE, 0, 0, 0, 0, 1, true, false);
        assertEquals(255, absurd.sharpness());
        assertTrue(Double.isFinite(ItemScoring.weaponScore(absurd, TargetFamily.GENERIC, 0.5)));
    }

    /**
     * An empty slot must not turn a refusal into the winning candidate.
     *
     * <p>{@link InventorySelection#best} picks anything strictly above its baseline, and the scorers
     * that feed it say "not this one" by returning -1. So a baseline below -1 selects a refusal.
     * That is exactly what an empty armour slot used to produce: findBestArmor floored at negative
     * infinity, so with nothing wearable in the bag it answered "inventory slot 0" instead of
     * "nothing", and AutoArmor tried to wear whatever happened to be there.
     *
     * <p>The first assertion is the trap itself, kept so the mechanism stays visible; the second is
     * the rule that closes it.
     */
    @Test void anEmptySlotIsFlooredAboveTheRefusalSentinel() {
        assertEquals(0, InventorySelection.best(5, Double.NEGATIVE_INFINITY, slot -> -1),
                "the trap: with no floor, every slot refusing still selects the first one");

        double baseline = ItemScoring.upgradeBaseline(Double.NEGATIVE_INFINITY, 0);
        assertTrue(baseline >= 0, "an empty slot must floor at zero, not below the sentinel");
        assertEquals(-1, InventorySelection.best(5, baseline, slot -> -1),
                "every slot refused, so nothing may be selected");
    }

    /** A real candidate still wins from an empty slot, which is the case the floor must not break. */
    @Test void anEmptySlotStillAcceptsSomethingWearable() {
        double baseline = ItemScoring.upgradeBaseline(Double.NEGATIVE_INFINITY, 0);
        assertEquals(2, InventorySelection.best(5, baseline, slot -> slot == 2 ? 7.5 : -1));
    }

    /** The extracted rule and the predicate that used to own it must not drift apart. */
    @Test void theBaselineAndTheUpgradeRuleAgree() {
        double[] currents = { Double.NEGATIVE_INFINITY, 0, 3.25, 10 };
        double[] improvements = { 0, 0.5, 5 };
        double[] candidates = { -1, 0, 0.5, 3.24, 3.26, 10.4, 10.6, 99 };
        for (double current : currents) {
            for (double improvement : improvements) {
                for (double candidate : candidates) {
                    assertEquals(candidate > ItemScoring.upgradeBaseline(current, improvement),
                            ItemScoring.isUpgrade(candidate, current, improvement),
                            "candidate=" + candidate + " current=" + current + " min=" + improvement);
                }
            }
        }
    }
}
