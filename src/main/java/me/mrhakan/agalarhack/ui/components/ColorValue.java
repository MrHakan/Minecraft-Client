package me.mrhakan.agalarhack.ui.components;

/** Color clipboard format is #RRGGBBAA; the renderer representation is packed ARGB. */
public final class ColorValue {
    private ColorValue() { }
    public static int parse(String text) {
        String hex=text.trim().replaceFirst("^#","");
        if(!hex.matches("[0-9a-fA-F]{6}([0-9a-fA-F]{2})?"))throw new IllegalArgumentException("Use #RRGGBB or #RRGGBBAA");
        long rgba=Long.parseLong(hex,16);
        return hex.length()==6?(int)(0xff000000L|rgba):(int)((rgba&255)<<24|(rgba>>>8));
    }
    public static String hex(int argb){return String.format(java.util.Locale.ROOT,"#%06X%02X",argb&0xffffff,argb>>>24);}
    public static int hsv(float hue,float saturation,float value,int alpha){
        return (Math.max(0,Math.min(255,alpha))<<24)|(java.awt.Color.HSBtoRGB(hue,saturation,value)&0xffffff);
    }
    public static float[] hsv(int argb){return java.awt.Color.RGBtoHSB((argb>>>16)&255,(argb>>>8)&255,argb&255,null);}
}
