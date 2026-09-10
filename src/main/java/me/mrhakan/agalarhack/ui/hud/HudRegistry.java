package me.mrhakan.agalarhack.ui.hud;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import me.mrhakan.agalarhack.AgalarHackClient;
import me.mrhakan.agalarhack.events.ClientEvents;
import me.mrhakan.agalarhack.managers.HudLayoutManager;

/** Dynamic registration with stable IDs shared by rendering, persistence and the editor. */
public final class HudRegistry {
    public record Component(String id,String title,IntSupplier width,IntSupplier height,Consumer<ClientEvents.HudRender> render) { }
    private final Map<String,Component> components=new LinkedHashMap<>();
    private final HudLayoutManager layout;
    public HudRegistry(HudLayoutManager layout){this.layout=layout;}
    public void register(Component component,HudLayoutManager.WidgetState defaults){
        if(!component.id().matches("[a-z0-9_.-]{1,64}"))throw new IllegalArgumentException("Invalid HUD ID");
        if(components.putIfAbsent(component.id(),component)!=null)throw new IllegalStateException("Duplicate HUD ID: "+component.id());
        layout.registerDefault(component.id(),defaults);
    }
    public List<String> ids(){return components.keySet().stream().sorted(java.util.Comparator.comparingInt(id->layout.get(id).zOrder)).toList();}
    public Component get(String id){return components.get(id);}
    public void render(ClientEvents.HudRender event){
        for(String id:ids()){
            Component component=components.get(id);
            if(!layout.get(component.id()).visible)continue;
            try{component.render().accept(event);}
            catch(RuntimeException failure){
                layout.get(component.id()).visible=false;
                AgalarHackClient.LOGGER.error("HUD component disabled: {}",component.id(),failure);
            }
        }
    }
}
