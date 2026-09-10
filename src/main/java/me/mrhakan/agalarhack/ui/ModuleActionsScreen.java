package me.mrhakan.agalarhack.ui;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.mrhakan.agalarhack.AgalarHackClient;
import me.mrhakan.agalarhack.module.Module;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Accessible context actions. Paste validates all setting values before applying any. */
public final class ModuleActionsScreen extends Screen implements me.mrhakan.agalarhack.ui.ClientScreen {
    @Override public Screen parentScreen() { return parent; }
    private final Screen parent;
    private final Module module;
    private String feedback="";
    public ModuleActionsScreen(Screen parent,Module module){super(Component.literal(module.getName()));this.parent=parent;this.module=module;}
    @Override public void init(){
        String[] labels={"Toggle","Favorite","Bind","Reset settings","Copy settings","Paste settings"};
        for(int i=0;i<labels.length;i++){
            final int action=i;
            addRenderableWidget(Button.builder(Component.literal(labels[i]),b->act(action)).bounds(width/2-110,40+i*24,220,20).build());
        }
        addRenderableWidget(Button.builder(Component.literal("Back"),b->onClose()).bounds(width/2-40,height-28,80,20).build());
    }
    private void act(int action){
        try {
            switch(action){
                case 0 -> module.toggle();
                case 1 -> module.settings.setSetting("favorite",!module.getBooleanSetting("favorite",false));
                case 2 -> { minecraft.gui.setScreen(new KeybindCaptureScreen(this,module)); return; }
                case 3 -> { for(var spec:module.settings.getSpecs()) if(editable(spec.getName())) module.settings.setSetting(spec.getName(),spec.getDefaultValue()); }
                case 4 -> {
                    JsonObject data=new JsonObject();data.addProperty("module",module.getName());JsonObject values=new JsonObject();
                    for(var spec:module.settings.getSpecs())if(editable(spec.getName())) values.add(spec.getName(),new Gson().toJsonTree(module.settings.getSetting(spec.getName())));
                    data.add("settings",values);minecraft.keyboardHandler.setClipboard(data.toString());feedback="Copied settings";return;
                }
                case 5 -> {
                    String raw=minecraft.keyboardHandler.getClipboard();if(raw.length()>65536)throw new IllegalArgumentException("Clipboard is too large");
                    var root=JsonParser.parseString(raw).getAsJsonObject();
                    if(!root.get("module").getAsString().equals(module.getName()))throw new IllegalArgumentException("Settings belong to a different module");
                    var values=root.getAsJsonObject("settings");var parsed=new java.util.LinkedHashMap<String,Object>();
                    for(var entry:values.entrySet()){
                        if(!editable(entry.getKey()) || module.settings.getSpecIgnoreCase(entry.getKey())==null)throw new IllegalArgumentException("Unknown or protected setting");
                        parsed.put(entry.getKey(),module.settings.parseSettingValue(entry.getKey(),entry.getValue().getAsString()));
                    }
                    parsed.forEach(module.settings::setSetting);
                }
            }
            AgalarHackClient.SETTINGS_MANAGER.updateSettings();feedback="Saved";
        } catch(RuntimeException failure){feedback="Could not apply: "+failure.getMessage();}
    }
    public static boolean editable(String key){return !java.util.Set.of("enabled","keybind","keyModifiers","favorite","lastUsed").contains(key);}
    @Override public void onClose(){minecraft.gui.setScreen(parent);}
    @Override public void extractBackground(GuiGraphicsExtractor g,int x,int y,float d){ClientUiTheme.backdrop(g,width,height);}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float d){super.extractRenderState(g,x,y,d);g.centeredText(font,module.getName()+" actions",width/2,15,ClientUiTheme.TEXT);g.centeredText(font,font.plainSubstrByWidth(feedback,Math.max(10,width-24)),width/2,height-42,ClientUiTheme.MUTED);}
}
