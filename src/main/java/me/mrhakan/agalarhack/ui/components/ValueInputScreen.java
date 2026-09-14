package me.mrhakan.agalarhack.ui.components;
import java.util.function.Consumer;
import me.mrhakan.agalarhack.ui.ClientUiTheme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
/** Exact text entry complements sliders for values requiring precision. */
public final class ValueInputScreen extends Screen implements me.mrhakan.agalarhack.ui.ClientScreen {
    @Override public Screen parentScreen() { return parent; }
    private final Screen parent;private final String initial;private final Consumer<String> save;
    private EditBox input;private String error="";
    public ValueInputScreen(Screen parent,String title,String initial,Consumer<String> save){super(Component.literal(title));this.parent=parent;this.initial=initial;this.save=save;}
    @Override public void init(){input=new EditBox(font,width/2-120,height/2-15,240,20,getTitle());input.setValue(initial);addRenderableWidget(input);setInitialFocus(input);
        addRenderableWidget(Button.builder(Component.literal("Apply"),b->{try{save.accept(input.getValue());onClose();}catch(IllegalArgumentException failure){error=failure.getMessage();}}).bounds(width/2-90,height/2+20,80,20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"),b->onClose()).bounds(width/2+10,height/2+20,80,20).build());}
    @Override public void onClose(){minecraft.gui.setScreen(parent);}
    @Override public void extractBackground(GuiGraphicsExtractor g,int x,int y,float d){ClientUiTheme.backdrop(g,width,height);}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float d){super.extractRenderState(g,x,y,d);g.centeredText(font,getTitle(),width/2,height/2-40,ClientUiTheme.TEXT);g.centeredText(font,font.plainSubstrByWidth(error,width-24),width/2,height/2+50,ClientUiTheme.DANGER);}
}
