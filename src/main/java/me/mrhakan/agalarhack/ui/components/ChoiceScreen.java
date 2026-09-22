package me.mrhakan.agalarhack.ui.components;

import java.util.List;
import java.util.function.Consumer;
import me.mrhakan.agalarhack.ui.ClientUiTheme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
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
        int buttonWidth=Math.min(280,Math.max(120,width-32));
        for(int i=start;i<Math.min(choices.size(),start+count);i++) {
            String choice=choices.get(i);
            String label=font.plainSubstrByWidth(choice,Math.max(20,buttonWidth-12));
            if(label.length()<choice.length() && label.length()>1) label=label.substring(0,label.length()-1)+"…";
            addRenderableWidget(Button.builder(Component.literal(label),b->{selected.accept(choice);onClose();})
                    .bounds(width/2-buttonWidth/2,48+(i-start)*24,buttonWidth,20).build());
        }
        if(page>0)addRenderableWidget(Button.builder(Component.literal("‹ Prev"),b->{page--;rebuild();}).bounds(width/2-126,height-35,72,20).build());
        if(start+count<choices.size())addRenderableWidget(Button.builder(Component.literal("Next ›"),b->{page++;rebuild();}).bounds(width/2+54,height-35,72,20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"),b->onClose()).bounds(width/2-40,height-35,80,20).build());
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
