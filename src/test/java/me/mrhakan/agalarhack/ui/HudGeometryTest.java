package me.mrhakan.agalarhack.ui;
import me.mrhakan.agalarhack.ui.hud.HudGeometry;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class HudGeometryTest {
    @Test void touchingEdgesAreNotOverlap(){assertFalse(HudGeometry.overlaps(0,0,10,10,10,0,10,10));assertTrue(HudGeometry.overlaps(0,0,10,10,9,9,10,10));}
    @Test void gridAndClampHandleInvalidBounds(){assertEquals(20,HudGeometry.grid(18,10));assertEquals(5,HudGeometry.clamp(3,5,1));assertEquals(0,HudGeometry.grid(0,0));}
}
