package me.mrhakan.agalarhack.services;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RotationMathTest {
    @Test void yawTakesShortestPathAndPitchCannotFlip() {
        assertEquals(181, RotationMath.stepYaw(179, -179, 10));
        assertEquals(5, RotationMath.stepYaw(0, 90, 5));
        assertEquals(90, RotationMath.stepPitch(89, 200, 20));
        assertEquals(0, RotationMath.stepYaw(0, Float.NaN, 10));
        assertEquals(0, RotationMath.stepYaw(0, 90, -1));
    }
}
