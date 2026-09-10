package me.mrhakan.agalarhack.ui.hud;
public final class HudGeometry {
    private HudGeometry() { }
    public static int clamp(int value,int minimum,int maximum){return Math.max(minimum,Math.min(Math.max(minimum,maximum),value));}
    public static int grid(int value,int size){int safe=Math.max(1,Math.min(64,size));return Math.round(value/(float)safe)*safe;}
    public static boolean overlaps(int ax,int ay,int aw,int ah,int bx,int by,int bw,int bh){return ax<bx+bw&&ax+aw>bx&&ay<by+bh&&ay+ah>by;}
}
