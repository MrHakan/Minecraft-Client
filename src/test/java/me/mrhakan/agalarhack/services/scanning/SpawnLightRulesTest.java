package me.mrhakan.agalarhack.services.scanning;

import me.mrhakan.agalarhack.services.scanning.SpawnLightRules.Spawnable;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SpawnLightRulesTest {
    @Test void darknessEverywhereIsSpawnableAtAnyTime() {
        assertEquals(Spawnable.ALWAYS, SpawnLightRules.classify(0, 0, true));
    }

    @Test void skyLitButUnlitByBlocksIsNightOnly() {
        assertEquals(Spawnable.NIGHT, SpawnLightRules.classify(0, 15, true));
        assertEquals(Spawnable.NIGHT, SpawnLightRules.classify(0, 4, true));
    }

    @Test void anyBlockLightPreventsSpawning() {
        for (int light = 1; light <= 15; light++) {
            assertEquals(Spawnable.NONE, SpawnLightRules.classify(light, 0, true), "block light " + light);
        }
    }

    @Test void aPositionWithNoRoomToStandIsNeverMarked() {
        assertEquals(Spawnable.NONE, SpawnLightRules.classify(0, 0, false));
    }

    @Test void outOfRangeLightValuesAreRejectedRatherThanClamped() {
        assertEquals(Spawnable.NONE, SpawnLightRules.classify(-1, 0, true));
        assertEquals(Spawnable.NONE, SpawnLightRules.classify(16, 0, true));
        assertEquals(Spawnable.NONE, SpawnLightRules.classify(0, -1, true));
        assertEquals(Spawnable.NONE, SpawnLightRules.classify(0, 16, true));
    }

    @Test void anySkyLightLevelIsNightSpawnableWhenBlockLightIsZero() {
        // The sky darkens at night whatever its daytime level, so the daytime value does not change
        // the classification - only whether the spot is enclosed does.
        for (int sky = 1; sky <= 15; sky++) {
            assertEquals(Spawnable.NIGHT, SpawnLightRules.classify(0, sky, true), "sky light " + sky);
        }
    }
}
