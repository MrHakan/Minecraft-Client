package me.mrhakan.agalarhack.services.scanning;

import me.mrhakan.agalarhack.services.scanning.HoleDetector.Hole;
import me.mrhakan.agalarhack.services.scanning.HoleDetector.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HoleDetectorTest {
    private static Material[] sides(Material all) {
        return new Material[] { all, all, all, all };
    }

    @Test void fullyEnclosedResistantBlocksAreSafe() {
        assertEquals(Hole.SAFE, HoleDetector.classify(Material.RESISTANT, sides(Material.RESISTANT), true, true));
    }

    @Test void oneWeakSideMakesTheHoleUnsafeRatherThanSafe() {
        Material[] sides = sides(Material.RESISTANT);
        sides[2] = Material.WEAK;
        assertEquals(Hole.UNSAFE, HoleDetector.classify(Material.RESISTANT, sides, true, true));
    }

    @Test void aWeakFloorAlsoDowngradesIt() {
        assertEquals(Hole.UNSAFE, HoleDetector.classify(Material.WEAK, sides(Material.RESISTANT), true, true));
    }

    @Test void anyOpenSideMeansItIsNotAHole() {
        Material[] sides = sides(Material.RESISTANT);
        sides[0] = Material.OPEN;
        assertEquals(Hole.NONE, HoleDetector.classify(Material.RESISTANT, sides, true, true));
    }

    @Test void anOpenFloorMeansItIsNotAHole() {
        assertEquals(Hole.NONE, HoleDetector.classify(Material.OPEN, sides(Material.RESISTANT), true, true));
    }

    @Test void aHoleWithNoRoomToStandIsNotMarked() {
        assertEquals(Hole.NONE, HoleDetector.classify(Material.RESISTANT, sides(Material.RESISTANT), false, true));
        assertEquals(Hole.NONE, HoleDetector.classify(Material.RESISTANT, sides(Material.RESISTANT), true, false));
    }

    @Test void allWeakIsStillAHoleJustNotASafeOne() {
        assertEquals(Hole.UNSAFE, HoleDetector.classify(Material.WEAK, sides(Material.WEAK), true, true));
    }

    @Test void malformedInputIsRejectedRatherThanGuessed() {
        assertEquals(Hole.NONE, HoleDetector.classify(null, sides(Material.RESISTANT), true, true));
        assertEquals(Hole.NONE, HoleDetector.classify(Material.RESISTANT, null, true, true));
        assertEquals(Hole.NONE, HoleDetector.classify(Material.RESISTANT, new Material[] { Material.RESISTANT }, true, true));
        Material[] withNull = sides(Material.RESISTANT);
        withNull[1] = null;
        assertEquals(Hole.NONE, HoleDetector.classify(Material.RESISTANT, withNull, true, true));
    }
}
