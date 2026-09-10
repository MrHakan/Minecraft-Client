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
    public ChoiceScreen(Screen parent,String title,List<String> choices,Consumer<String> selected) {
        super(Component.literal(title)); this.parent=parent; this.choices=List.copyOf(choices); this.selected=selected;
    }
    @Override public void init() {
        int count=Math.max(1,(height-90)/24), start=page*count;
        for(int i=start;i<Math.min(choices.size(),start+count);i++) {
            String choice=choices.get(i);
            addRenderableWidget(Button.builder(Component.literal(choice),b->{selected.accept(choice);onClose();})
                    .bounds(Math.max(8,width/2-120),40+(i-start)*24,Math.min(240,width-16),20).build());
        }
        if(page>0)addRenderableWidget(Button.builder(Component.literal("Prev"),b->{page--;rebuild();}).bounds(width/2-120,height-35,60,20).build());
        if(start+count<choices.size())addRenderableWidget(Button.builder(Component.literal("Next"),b->{page++;rebuild();}).bounds(width/2+60,height-35,60,20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"),b->onClose()).bounds(width/2-40,height-35,80,20).build());
    }
    private void rebuild(){ clearWidgets();init(); }
    @Override public void onClose(){ minecraft.gui.setScreen(parent); }
    @Override public void extractBackground(GuiGraphicsExtractor g,int x,int y,float d){ClientUiTheme.backdrop(g,width,height);}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float d){super.extractRenderState(g,x,y,d);g.centeredText(font,getTitle(),width/2,16,ClientUiTheme.TEXT);}
}
