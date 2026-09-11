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
}
