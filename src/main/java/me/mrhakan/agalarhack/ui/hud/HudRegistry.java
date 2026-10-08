package me.mrhakan.agalarhack.ui.hud;

import java.util.ArrayList;
import java.util.Comparator;
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
    /** Registration order, indexable so the per-frame staleness check needs no iterator. */
    private final List<String> registered=new ArrayList<>();
    private final Set<String> failed=new HashSet<>();
    private final HudLayoutManager layout;
    /** Draw order and the z-order each widget had when it was computed. */
    private List<String> order=List.of();
    private int[] orderedBy=new int[0];
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
        registered.add(component.id());
        layout.registerDefault(component.id(),defaults);
    }

    /**
     * Widgets in draw order: ascending z-order, registration order among equals.
     *
     * <p>Runs every frame, so the sorted list is kept and only rebuilt when it is stale. There is no
     * change event to hook - the editor writes z-order straight into the widget state, and a profile
     * or layout load replaces the states wholesale - so staleness is detected by comparing each
     * widget's current z-order with the one the order was built from. That check is a pass over an
     * int array; the sort and the list it replaced were an allocation and a sort per frame.
     */
    public List<String> ids(){
        int count=registered.size();
        boolean stale=orderedBy.length!=count;
        for(int index=0;!stale&&index<count;index++)stale=layout.get(registered.get(index)).zOrder!=orderedBy[index];
        if(stale){
            int[] keys=new int[count];
            for(int index=0;index<count;index++)keys[index]=layout.get(registered.get(index)).zOrder;
            order=registered.stream().sorted(Comparator.comparingInt(id->layout.get(id).zOrder)).toList();
            orderedBy=keys;
        }
        return order;
    }
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
