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

public final class ThemeScreen extends Screen implements me.mrhakan.agalarhack.ui.ClientScreen {
    @Override public Screen parentScreen() { return parent; }
    private final Screen parent;private final ThemeService service=ClientServices.require(ThemeService.class);
    private final ThemeService.Theme original;private ThemeService.Theme editing;private String feedback="";
    private int page;
    public ThemeScreen(Screen parent){super(Component.literal("Themes"));this.parent=parent;original=service.copy();editing=service.copy();}
    @Override public void init(){
        int w=Math.max(80,Math.min(280,width-16)),x=(width-w)/2;
        List<java.util.function.IntConsumer> rows=List.of(
            y->button("Preset: "+editing.name,x,y,w,()->minecraft.gui.setScreen(new ChoiceScreen(this,"Theme preset",ThemeService.PRESETS,name->{
                // Palette changes preserve accessibility choices.
                var next=service.preset(name);next.reducedMotion=editing.reducedMotion;next.highContrast=editing.highContrast;
                next.uiAnimations=editing.uiAnimations;editing=next;service.preview(editing);
            }))),
            y->button("Edit color",x,y,w,()->minecraft.gui.setScreen(new ChoiceScreen(this,"Theme colors",List.of("Accent","Background","Panel","Text","Muted","Module ON","Module OFF"),this::chooseColor))),
            y->button("Shadows: "+editing.shadows,x,y,w,()->{editing.shadows=!editing.shadows;refresh();}),
            y->addRenderableWidget(new NumberSlider(x,y,w,"Panel opacity",0.2,1,editing.panelOpacity,v->{editing.panelOpacity=v;service.preview(editing);})),
            y->addRenderableWidget(new NumberSlider(x,y,w,"Corner radius",0,12,editing.cornerRadius,v->{editing.cornerRadius=(int)Math.round(v);service.preview(editing);})),
            y->addRenderableWidget(new NumberSlider(x,y,w,"Animation speed",0.25,4,editing.animationSpeed,v->{editing.animationSpeed=v;service.preview(editing);})),
            y->button("Animations: "+editing.uiAnimations,x,y,w,()->{editing.uiAnimations=!editing.uiAnimations;refresh();}),
            y->button("Reduced motion: "+editing.reducedMotion,x,y,w,()->{editing.reducedMotion=!editing.reducedMotion;refresh();}),
            y->button("High contrast: "+editing.highContrast,x,y,w,()->{editing.highContrast=!editing.highContrast;refresh();}),
            y->button("Export theme",x,y,w,()->minecraft.keyboardHandler.setClipboard(service.export(editing))),
            y->button("Import theme",x,y,w,()->{try{editing=service.parse(minecraft.keyboardHandler.getClipboard());refresh();}catch(RuntimeException e){feedback="Invalid theme: "+e.getMessage();}})
        );
        int count=Math.max(1,(height-100)/24),pages=(rows.size()+count-1)/count;
        page=Math.clamp(page,0,pages-1);
        for(int i=page*count;i<Math.min(rows.size(),(page+1)*count);i++)rows.get(i).accept(34+(i-page*count)*24);
        int cell=(w-12)/4;
        if(page>0)button("Prev",x,height-28,cell,()->{page--;refresh();});
        button("Save",x+cell+4,height-28,cell,()->{service.preview(editing);if(service.save()){saved=true;minecraft.gui.setScreen(parent);}else feedback="Could not save theme";});
        button("Cancel",x+2*(cell+4),height-28,cell,this::onClose);
        if(page+1<pages)button("Next",x+3*(cell+4),height-28,cell,()->{page++;refresh();});
    }
    private void button(String title,int x,int y,int w,Runnable action){addRenderableWidget(Button.builder(Component.literal(title),b->action.run()).bounds(x,y,w,20).build());}
    private void chooseColor(String name){
        int initial=switch(name){case "Background"->editing.background;case "Panel"->editing.panel;case "Text"->editing.text;case "Muted"->editing.muted;case "Module ON"->editing.on;case "Module OFF"->editing.off;default->editing.accent;};
        // ChoiceScreen returns to this screen after its callback; defer opening the color editor to the next tick.
        pendingColor=()->minecraft.gui.setScreen(new ColorPickerScreen(this,initial,color->{
            switch(name){case "Background"->editing.background=color;case "Panel"->editing.panel=color;case "Text"->editing.text=color;case "Muted"->editing.muted=color;case "Module ON"->editing.on=color;case "Module OFF"->editing.off=color;default->editing.accent=color;}
            service.preview(editing);
        }));
    }
    private Runnable pendingColor;
    private boolean saved;
    @Override public void abandoned() { pendingColor=null; if (!saved) service.preview(original); }
    @Override public void tick(){if(pendingColor!=null){Runnable next=pendingColor;pendingColor=null;next.run();}}
    private void refresh(){service.preview(editing);clearWidgets();init();}
    @Override public void onClose(){service.preview(original);minecraft.gui.setScreen(parent);}
    @Override public void extractBackground(GuiGraphicsExtractor g,int x,int y,float d){ClientUiTheme.backdrop(g,width,height);int w=Math.min(296,width-8);ClientUiTheme.panel(g,(width-w)/2,26,w,Math.max(24,height-80),false);}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float d){super.extractRenderState(g,x,y,d);g.centeredText(font,"THEMES • "+(page+1),width/2,12,ClientUiTheme.TEXT);g.centeredText(font,font.plainSubstrByWidth(feedback,Math.max(1,width-16)),width/2,height-42,ClientUiTheme.MUTED);}
}
