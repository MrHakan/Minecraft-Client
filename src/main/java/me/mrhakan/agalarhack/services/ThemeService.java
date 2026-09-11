package me.mrhakan.agalarhack.services;

import java.util.List;
import me.mrhakan.agalarhack.AgalarHackClient;
import me.mrhakan.agalarhack.ui.ClientUiTheme;
import net.fabricmc.loader.api.FabricLoader;

/** Visual preferences are stored separately from gameplay profiles. */
public final class ThemeService {
    public static final List<String> PRESETS=List.of("Default Dark","AMOLED","Light","Ocean","Crimson","Purple");
    public static final class Theme {
        public int schemaVersion=1;
        public String name="Default Dark";
        public int background=0xff0b1017,panel=0xff16202d,accent=0xff58a6ff,text=0xfff0f5fa,muted=0xff8fa3b8,on=0xff62d68b,off=0xff526379;
        public double panelOpacity=0.92,animationSpeed=1;
        public int cornerRadius=4;
        public boolean shadows=true,uiAnimations=true;
        public boolean reducedMotion=false,highContrast=false;
        public boolean motionEnabled() { return uiAnimations && !reducedMotion; }
    }
    private final me.mrhakan.agalarhack.config.BoundedJsonFile<Theme> file =
            new me.mrhakan.agalarhack.config.BoundedJsonFile<>(
                    FabricLoader.getInstance().getConfigDir().resolve("agalarhack-theme.json"),
                    me.mrhakan.agalarhack.config.ThemeCodec.MAX_BYTES,
                    me.mrhakan.agalarhack.config.ThemeCodec::decode,
                    me.mrhakan.agalarhack.config.ThemeCodec::encode);
    private Theme current = new Theme();
    public Theme current() { return current; }
    public Theme copy() { return me.mrhakan.agalarhack.config.ThemeCodec.copy(current); }
    public Theme parse(String json) { return me.mrhakan.agalarhack.config.ThemeCodec.decode(json); }
    public String export(Theme value) { return me.mrhakan.agalarhack.config.ThemeCodec.encode(value); }
    public void preview(Theme value) { current = value; ClientUiTheme.apply(value); }
    public void load() {
        try { file.load().ifPresent(value -> current = value); }
        catch (java.io.IOException failure) {
            AgalarHackClient.LOGGER.warn("Theme preserved; saving disabled until successful reload", failure);
            ClientServices.registry().find(NotificationService.class).ifPresent(notifications ->
                    notifications.publish(NotificationService.Type.WARNING, "Unreadable theme retained; using current palette"));
        }
        preview(current);
    }
    public boolean save() {
        try { file.save(current); return true; }
        catch (java.io.IOException | IllegalArgumentException failure) {
            AgalarHackClient.LOGGER.error("Could not save theme", failure);
            ClientServices.registry().find(NotificationService.class).ifPresent(notifications ->
                    notifications.publish(NotificationService.Type.WARNING, "Theme could not be saved; export it to keep your changes"));
            return false;
        }
    }
    public Theme preset(String name){
        Theme theme=new Theme();theme.name=name;
        switch(name){
            case "AMOLED"->{theme.background=0xff000000;theme.panel=0xff080808;theme.accent=0xffeeeeee;}
            case "Light"->{theme.background=0xffedf1f6;theme.panel=0xffffffff;theme.text=0xff122030;theme.muted=0xff4c6074;theme.accent=0xff1764bc;theme.on=0xff17734a;theme.off=0xff647080;}
            case "Ocean"->{theme.background=0xff061a24;theme.panel=0xff102d3b;theme.accent=0xff38d3d9;}
            case "Crimson"->{theme.background=0xff1b0c13;theme.panel=0xff301620;theme.accent=0xffff5277;}
            case "Purple"->{theme.background=0xff140e24;theme.panel=0xff24183d;theme.accent=0xffb391ff;}
        }
        return theme;
    }
}
