package me.mrhakan.agalarhack.ui;
import java.util.List;
import me.mrhakan.agalarhack.services.ClientServices;
import me.mrhakan.agalarhack.services.ThemeService;
import me.mrhakan.agalarhack.ui.components.ChoiceScreen;
import me.mrhakan.agalarhack.ui.components.NumberSlider;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class ThemeScreen extends Screen {
    private final Screen parent;private final ThemeService service=ClientServices.require(ThemeService.class);
    private final ThemeService.Theme original;private ThemeService.Theme editing;private String feedback="";
    public ThemeScreen(Screen parent){super(Component.literal("Themes"));this.parent=parent;original=service.copy();editing=service.copy();}
    @Override public void init(){
        int x=width/2-140;
        addRenderableWidget(Button.builder(Component.literal("Preset: "+editing.name),b->minecraft.gui.setScreen(new ChoiceScreen(this,"Theme preset",ThemeService.PRESETS,name->{editing=service.preset(name);service.preview(editing);}))).bounds(x,34,280,20).build());
        addRenderableWidget(Button.builder(Component.literal("Edit color"),b->minecraft.gui.setScreen(new ChoiceScreen(this,"Theme colors",List.of("Accent","Background","Panel","Text","Muted","Module ON","Module OFF"),this::chooseColor))).bounds(x,58,136,20).build());
        addRenderableWidget(Button.builder(Component.literal("Shadows: "+editing.shadows),b->{editing.shadows=!editing.shadows;refresh();}).bounds(x+144,58,136,20).build());
        addRenderableWidget(new NumberSlider(x,84,280,"Panel opacity",0.2,1,editing.panelOpacity,v->{editing.panelOpacity=v;service.preview(editing);}));
        addRenderableWidget(new NumberSlider(x,108,280,"Corner radius",0,12,editing.cornerRadius,v->{editing.cornerRadius=(int)Math.round(v);service.preview(editing);}));
        addRenderableWidget(new NumberSlider(x,132,280,"Animation speed",0.25,4,editing.animationSpeed,v->editing.animationSpeed=v));
        addRenderableWidget(Button.builder(Component.literal("Animations: "+editing.uiAnimations),b->{editing.uiAnimations=!editing.uiAnimations;refresh();}).bounds(x,156,136,20).build());
        addRenderableWidget(Button.builder(Component.literal("Export"),b->minecraft.keyboardHandler.setClipboard(service.export(editing))).bounds(x+144,156,64,20).build());
        addRenderableWidget(Button.builder(Component.literal("Import"),b->{try{editing=service.parse(minecraft.keyboardHandler.getClipboard());service.preview(editing);refresh();}catch(RuntimeException e){feedback=e.getMessage();}}).bounds(x+216,156,64,20).build());
        addRenderableWidget(Button.builder(Component.literal("Save"),b->{service.preview(editing);if(service.save())minecraft.gui.setScreen(parent);else feedback="Could not save theme";}).bounds(width/2-85,height-28,80,20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"),b->onClose()).bounds(width/2+5,height-28,80,20).build());
    }
    private void chooseColor(String name){
        int initial=switch(name){case "Background"->editing.background;case "Panel"->editing.panel;case "Text"->editing.text;case "Muted"->editing.muted;case "Module ON"->editing.on;case "Module OFF"->editing.off;default->editing.accent;};
        // ChoiceScreen returns to this screen after its callback; defer opening the color editor to the next tick.
        pendingColor=()->minecraft.gui.setScreen(new ColorPickerScreen(this,initial,color->{
            switch(name){case "Background"->editing.background=color;case "Panel"->editing.panel=color;case "Text"->editing.text=color;case "Muted"->editing.muted=color;case "Module ON"->editing.on=color;case "Module OFF"->editing.off=color;default->editing.accent=color;}
            service.preview(editing);
        }));
    }
    private Runnable pendingColor;
    @Override public void tick(){if(pendingColor!=null){Runnable next=pendingColor;pendingColor=null;next.run();}}
    private void refresh(){service.preview(editing);clearWidgets();init();}
    @Override public void onClose(){service.preview(original);minecraft.gui.setScreen(parent);}
    @Override public void extractBackground(GuiGraphicsExtractor g,int x,int y,float d){ClientUiTheme.backdrop(g,width,height);ClientUiTheme.panel(g,width/2-148,26,296,164,false);}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float d){super.extractRenderState(g,x,y,d);g.centeredText(font,"THEMES",width/2,12,ClientUiTheme.TEXT);g.centeredText(font,feedback,width/2,height-42,ClientUiTheme.MUTED);}
}
