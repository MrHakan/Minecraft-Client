package me.mrhakan.agalarhack.services;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
    }
    private final Gson gson=new GsonBuilder().setPrettyPrinting().create();
    private final Path path=FabricLoader.getInstance().getConfigDir().resolve("agalarhack-theme.json");
    private Theme current=new Theme();
    private boolean writable=true;
    public Theme current(){return current;}
    public Theme copy(){return gson.fromJson(gson.toJson(current),Theme.class);}
    public Theme parse(String json){
        if(json==null||json.length()>16384)throw new IllegalArgumentException("Theme is too large");
        Theme value=gson.fromJson(json,Theme.class);
        if(value==null||value.schemaVersion!=1)throw new IllegalArgumentException("Unsupported theme schema");
        if(!Double.isFinite(value.panelOpacity)||!Double.isFinite(value.animationSpeed))throw new IllegalArgumentException("Theme numbers must be finite");
        value.panelOpacity=Math.max(0.2,Math.min(1,value.panelOpacity));value.animationSpeed=Math.max(0.25,Math.min(4,value.animationSpeed));
        value.cornerRadius=Math.max(0,Math.min(12,value.cornerRadius));
        if(value.name==null)value.name="Custom";if(value.name.length()>40)value.name=value.name.substring(0,40);
        return value;
    }
    public String export(Theme value){return gson.toJson(value);}
    public void preview(Theme value){current=value;ClientUiTheme.apply(value);}
    public void load(){
        if(Files.isRegularFile(path))try{
            if(Files.size(path)>16384)throw new IllegalArgumentException("Theme exceeds limit");
            current=parse(Files.readString(path));
        }catch(Exception failure){writable=false;AgalarHackClient.LOGGER.error("Theme retained after load failure",failure);}
        preview(current);
    }
    public boolean save(){
        if(!writable){ClientServices.require(NotificationService.class).publish(NotificationService.Type.WARNING,"Original unreadable theme retained; export your theme instead");return false;}
        Path temporary=null;
        try{
            Files.createDirectories(path.getParent());temporary=Files.createTempFile(path.getParent(),"agalar-theme-",".tmp");Files.writeString(temporary,export(current));
            try{Files.move(temporary,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(java.nio.file.AtomicMoveNotSupportedException ignored){Files.move(temporary,path,StandardCopyOption.REPLACE_EXISTING);}
            return true;
        }catch(Exception failure){AgalarHackClient.LOGGER.error("Could not save theme",failure);return false;}
        finally{if(temporary!=null)try{Files.deleteIfExists(temporary);}catch(java.io.IOException ignored){}}
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
