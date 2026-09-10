package me.mrhakan.agalarhack.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import me.mrhakan.agalarhack.ui.components.ColorValue;
import me.mrhakan.agalarhack.ui.components.NumberSlider;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** RGB/HSV/alpha editing with staged apply/cancel, hex and session recent colors. */
public final class ColorPickerScreen extends Screen implements me.mrhakan.agalarhack.ui.ClientScreen {
    @Override public Screen parentScreen() { return parent; }
    private static final List<Integer> RECENT=new ArrayList<>();
    private final Screen parent; private final IntConsumer apply; private int color; private boolean hsv;
    private EditBox hex; private String error="";
    public ColorPickerScreen(Screen parent,int initial,IntConsumer apply){super(Component.literal("Color picker"));this.parent=parent;this.color=initial;this.apply=apply;}
    @Override public void init(){
        int x=Math.max(8,width/2-140),w=Math.min(280,width-16);
        float[] components=ColorValue.hsv(color);
        String[] names=hsv?new String[]{"Hue","Saturation","Value"}:new String[]{"Red","Green","Blue"};
        for(int i=0;i<3;i++){
            int channel=i;double max=hsv?(i==0?360:100):255;
            double initial=hsv?components[i]*max:(color >>> (16-i*8))&255;
            addRenderableWidget(new NumberSlider(x,42+i*24,w,names[i],0,max,initial,value->{
                if(hsv){float[] values=ColorValue.hsv(color);values[channel]=(float)(value/max);color=ColorValue.hsv(values[0],values[1],values[2],color>>>24);}
                else {int shift=16-channel*8;color=(color&~(255<<shift))|((int)Math.round(value)<<shift);}
                refreshHex();
            }));
        }
        addRenderableWidget(new NumberSlider(x,114,w,"Alpha",0,255,color>>>24,value->{color=(color&0xffffff)|((int)Math.round(value)<<24);refreshHex();}));
        hex=new EditBox(font,x,140,w-64,20,Component.literal("Hex RGBA"));hex.setMaxLength(9);refreshHex();addRenderableWidget(hex);
        addRenderableWidget(Button.builder(Component.literal("Set"),b->parse(hex.getValue())).bounds(x+w-60,140,60,20).build());
        addRenderableWidget(Button.builder(Component.literal(hsv?"HSV":"RGB"),b->{hsv=!hsv;rebuild();}).bounds(x,166,56,20).build());
        addRenderableWidget(Button.builder(Component.literal("Copy"),b->minecraft.keyboardHandler.setClipboard(ColorValue.hex(color))).bounds(x+60,166,56,20).build());
        addRenderableWidget(Button.builder(Component.literal("Paste"),b->parse(minecraft.keyboardHandler.getClipboard())).bounds(x+120,166,56,20).build());
        for(int i=0;i<Math.min(4,RECENT.size());i++){int recent=RECENT.get(i);addRenderableWidget(Button.builder(Component.literal("●"),b->{color=recent;rebuild();}).bounds(x+i*32,192,28,20).build());}
        addRenderableWidget(Button.builder(Component.literal("Apply"),b->{apply.accept(color);RECENT.remove((Integer)color);RECENT.add(0,color);while(RECENT.size()>4)RECENT.remove(4);onClose();}).bounds(width/2-85,height-28,80,20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"),b->onClose()).bounds(width/2+5,height-28,80,20).build());
    }
    private void parse(String value){try{color=ColorValue.parse(value);error="";rebuild();}catch(IllegalArgumentException e){error=e.getMessage();}}
    private void refreshHex(){if(hex!=null)hex.setValue(ColorValue.hex(color));}
    private void rebuild(){clearWidgets();hex=null;init();}
    @Override public void onClose(){minecraft.gui.setScreen(parent);}
    @Override public void extractBackground(GuiGraphicsExtractor g,int x,int y,float d){ClientUiTheme.backdrop(g,width,height);g.fill(width/2-140,14,width/2+140,32,color);}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float d){super.extractRenderState(g,x,y,d);if(!error.isBlank())g.centeredText(font,error,width/2,height-42,ClientUiTheme.DANGER);}
}
