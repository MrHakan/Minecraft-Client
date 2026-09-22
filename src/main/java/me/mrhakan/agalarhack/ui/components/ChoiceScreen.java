package me.mrhakan.agalarhack.ui.components;

import java.util.List;
import java.util.function.Consumer;
import me.mrhakan.agalarhack.ui.ClientUiTheme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Keyboard-accessible choice modal, paginated to keep every option reachable. */
public final class ChoiceScreen extends Screen implements me.mrhakan.agalarhack.ui.ClientScreen {
    @Override public Screen parentScreen() { return parent; }
    private final Screen parent;
    private final List<String> choices;
    private final Consumer<String> selected;
    private int page;
    private int pageCount = 1;
    public ChoiceScreen(Screen parent,String title,List<String> choices,Consumer<String> selected) {
        super(Component.literal(title)); this.parent=parent; this.choices=List.copyOf(choices); this.selected=selected;
    }
    @Override public void init() {
        int count=Math.max(1,(height-104)/24);
        pageCount=Math.max(1,(choices.size()+count-1)/count);
        page=Math.max(0,Math.min(page,pageCount-1));
        int start=page*count;
        int buttonWidth=Math.min(280,Math.max(80,width-32));
        for(int i=start;i<Math.min(choices.size(),start+count);i++) {
            String choice=choices.get(i);
            String label=font.plainSubstrByWidth(choice,Math.max(20,buttonWidth-12));
            boolean truncated=label.length()<choice.length();
            if(truncated && label.length()>1) label=label.substring(0,label.length()-1)+"…";
            Button button=Button.builder(Component.literal(label),b->{selected.accept(choice);onClose();})
                    .bounds(width/2-buttonWidth/2,48+(i-start)*24,buttonWidth,20).build();
            // Truncation keeps narrow/high-scale layouts usable, but never hide the actual value.
            // Hovering the compact button exposes the complete choice without changing selection semantics.
            if(truncated) button.setTooltip(Tooltip.create(Component.literal(choice)));
            addRenderableWidget(button);
        }
        addNavigation(start,count);
    }
    /** Keeps pagination usable at high GUI scales instead of placing Prev/Next off-screen. */
    private void addNavigation(int start,int count) {
        int gap=4;
        int navWidth=Math.max(44,Math.min(72,(width-32-gap*2)/3));
        int total=navWidth*3+gap*2;
        int left=(width-total)/2;
        if(page>0)addRenderableWidget(Button.builder(Component.literal("‹ Prev"),b->{page--;rebuild();}).bounds(left,height-35,navWidth,20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"),b->onClose()).bounds(left+navWidth+gap,height-35,navWidth,20).build());
        if(start+count<choices.size())addRenderableWidget(Button.builder(Component.literal("Next ›"),b->{page++;rebuild();}).bounds(left+(navWidth+gap)*2,height-35,navWidth,20).build());
    }
    private void rebuild(){ clearWidgets();init(); }
    @Override public void onClose(){ minecraft.gui.setScreen(parent); }
    @Override public void extractBackground(GuiGraphicsExtractor g,int x,int y,float d){ClientUiTheme.backdrop(g,width,height);}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float d){
        super.extractRenderState(g,x,y,d);
        g.centeredText(font,getTitle(),width/2,14,ClientUiTheme.TEXT);
        String status=choices.isEmpty()?"No choices available":choices.size()+" choices  •  Page "+(page+1)+"/"+pageCount;
        g.centeredText(font,Component.literal(status),width/2,29,ClientUiTheme.MUTED);
    }
}
