package me.mrhakan.agalarhack.ui.hud;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import me.mrhakan.agalarhack.AgalarHackClient;
import me.mrhakan.agalarhack.events.ClientEvents;
import me.mrhakan.agalarhack.managers.HudLayoutManager;

/** Dynamic registration with stable IDs shared by rendering, persistence and the editor. */
public final class HudRegistry {
    public record Component(String id,String title,IntSupplier width,IntSupplier height,Consumer<ClientEvents.HudRender> render) { }
    private final Map<String,Component> components=new LinkedHashMap<>();
    private final Set<String> failed=new HashSet<>();
    private final HudLayoutManager layout;
    public HudRegistry(HudLayoutManager layout){this.layout=layout;}
    public void register(Component component,HudLayoutManager.WidgetState defaults){
        Objects.requireNonNull(component,"HUD component");
        Objects.requireNonNull(component.id(),"HUD ID");
        Objects.requireNonNull(component.title(),"HUD title");
        Objects.requireNonNull(component.width(),"HUD width");
        Objects.requireNonNull(component.height(),"HUD height");
        Objects.requireNonNull(component.render(),"HUD renderer");
        Objects.requireNonNull(defaults,"HUD defaults");
        Objects.requireNonNull(defaults.anchor,"HUD anchor");
        if(!component.id().matches("[a-z0-9_.-]{1,64}"))throw new IllegalArgumentException("Invalid HUD ID");
        if(component.title().isBlank()||component.title().length()>80)throw new IllegalArgumentException("Invalid HUD title");
        if(components.size()>=256)throw new IllegalStateException("HUD registry limit reached");
        if(components.putIfAbsent(component.id(),component)!=null)throw new IllegalStateException("Duplicate HUD ID: "+component.id());
        layout.registerDefault(component.id(),defaults);
    }
    public List<String> ids(){return components.keySet().stream().sorted(java.util.Comparator.comparingInt(id->layout.get(id).zOrder)).toList();}
    public Component get(String id){return components.get(id);}
    public boolean failed(String id){return failed.contains(id);}
    public void retry(String id){failed.remove(id);}
    public HudMeasurement measure(String id,int viewportWidth,int viewportHeight){
        Component component=components.get(id);
        if(component==null)throw new IllegalArgumentException("Unknown HUD component: "+id);
        if(!failed.contains(id))try{
            return HudMeasurement.measure(component.width(),component.height(),viewportWidth,viewportHeight);
        }catch(RuntimeException failure){isolate(id,failure);}
        return HudMeasurement.measure(()->70,()->30,viewportWidth,viewportHeight);
    }
    private void isolate(String id,RuntimeException failure){
        if(!failed.add(id))return;
        AgalarHackClient.LOGGER.error("HUD component suspended: {}",id,failure);
        me.mrhakan.agalarhack.services.ClientServices.registry().find(me.mrhakan.agalarhack.services.NotificationService.class)
                .ifPresent(notifications->notifications.publish(me.mrhakan.agalarhack.services.NotificationService.Type.ERROR,
                        "HUD error: "+id+"; retry in HUD editor"));
    }
    public void render(ClientEvents.HudRender event){
        for(String id:ids()){
            Component component=components.get(id);
            if(failed.contains(id)||!layout.get(component.id()).visible)continue;
            try{component.render().accept(event);}
            catch(RuntimeException failure){
                isolate(id,failure);
            }
        }
    }
}
