package me.mrhakan.agalarhack.ui;
import me.mrhakan.agalarhack.ui.components.ColorValue;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ColorValueTest {
    @Test void hexRoundTripsAlphaAndRejectsMalformedInput(){
        assertEquals(0x80ff6600,ColorValue.parse("#FF660080"));
        assertEquals(0xffff6600,ColorValue.parse("FF6600"));
        assertEquals("#FF660080",ColorValue.hex(0x80ff6600));
        assertThrows(IllegalArgumentException.class,()->ColorValue.parse("#GG0000"));
    }
    @Test void hsvPreservesPrimaryColors(){
        assertEquals(0xffff0000,ColorValue.hsv(0,1,1,255));
        float[] hsv=ColorValue.hsv(0xff336699);
        assertEquals(0xff336699,ColorValue.hsv(hsv[0],hsv[1],hsv[2],255));
    }
}
