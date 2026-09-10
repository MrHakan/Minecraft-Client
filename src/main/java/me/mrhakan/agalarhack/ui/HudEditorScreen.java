package me.mrhakan.agalarhack.ui;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import me.mrhakan.agalarhack.AgalarHackClient;
import me.mrhakan.agalarhack.managers.HudLayoutManager.WidgetState;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Drag-and-drop HUD editor with grid snapping and overlap diagnostics. */
public class HudEditorScreen extends Screen implements me.mrhakan.agalarhack.ui.ClientScreen {
    @Override public Screen parentScreen() { return parent; }
    private List<String> widgetOrder=List.of();
    private List<String> widgets(){return widgetOrder;}
    private int gridSize(){return AgalarHackClient.HUD_LAYOUT.editorOptions().gridSize;}
    private int snapThreshold(){return AgalarHackClient.HUD_LAYOUT.editorOptions().snapStrength;}
    private int safeMargin(){return AgalarHackClient.HUD_LAYOUT.editorOptions().safeMargin;}
    private int toolbarTop;
    private final Set<String> selection=new LinkedHashSet<>();

    private final Screen parent;
    private String selected;
    private String dragging;
    private double grabX;
    private double grabY;

    private final Map<String, Bounds> bounds = new LinkedHashMap<>();

    public HudEditorScreen(Screen parent) {
        this(parent, "branding");
    }

    private HudEditorScreen(Screen parent, String selected) {
        super(Component.literal("HUD Editor"));
        this.parent = parent;
        this.selected = widgets().contains(selected) ? selected : "branding";
        selection.add(this.selected);
    }

    @Override
    public void init() {
        super.init();
        widgetOrder=me.mrhakan.agalarhack.services.ClientServices.require(me.mrhakan.agalarhack.ui.hud.HudRegistry.class).ids();
        String[] labels={"Select","Anchor","Visible","Lock","Front","Grid","Grid size","Snap","Magnet","Margin","Reset","Back"};
        int columns=Math.max(1,width/66),rows=(labels.length+columns-1)/columns;
        toolbarTop=height-rows*24-8;
        for(int i=0;i<labels.length;i++){
            final int action=i;
            addRenderableWidget(Button.builder(Component.literal(labels[i]),button->{
                var layout=AgalarHackClient.HUD_LAYOUT;var options=layout.editorOptions();
                switch(action){
                    case 0->minecraft.gui.setScreen(new me.mrhakan.agalarhack.ui.components.ChoiceScreen(this,"HUD component",widgets(),id->{selected=id;selection.clear();selection.add(id);}));
                    case 1->layout.cycleAnchor(selected);
                    case 2->layout.toggleVisible(selected);
                    case 3->{layout.get(selected).locked=!layout.get(selected).locked;layout.save();}
                    case 4->{int index=0;for(String id:widgets())layout.get(id).zOrder=index++;layout.get(selected).zOrder=index;layout.save();}
                    case 5->{options.gridVisible=!options.gridVisible;layout.saveEditorOptions();}
                    case 6->{options.gridSize=options.gridSize>=20?5:options.gridSize+5;layout.saveEditorOptions();}
                    case 7->{options.snapping=!options.snapping;layout.saveEditorOptions();}
                    case 8->{options.snapStrength=(options.snapStrength+2)%14;layout.saveEditorOptions();}
                    case 9->{options.safeMargin=(options.safeMargin+4)%20;layout.saveEditorOptions();}
                    case 10->layout.reset(selected);
                    case 11->onClose();
                }
            }).bounds(4+(i%columns)*66,toolbarTop+(i/columns)*24,62,20).build());
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, width, height, 0xE010141C);
        if (!AgalarHackClient.HUD_LAYOUT.editorOptions().gridVisible) {
            return;
        }
        int limit = Math.max(0, toolbarTop - 6);
        for (int x = 0; x < width; x += gridSize()) {
            graphics.fill(x, 0, x + 1, limit, x % (gridSize() * 5) == 0 ? 0x303E526B : 0x182C394A);
        }
        for (int y = 0; y < limit; y += gridSize()) {
            graphics.fill(0, y, width, y + 1, y % (gridSize() * 5) == 0 ? 0x303E526B : 0x182C394A);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        if (event.button() != 0 || event.y() >= toolbarTop - 6) {
            return false;
        }

        updateBounds();
        for (int i = widgets().size() - 1; i >= 0; i--) {
            String id = widgets().get(i);
            Bounds b = bounds.get(id);
            if (b != null && b.contains(event.x(), event.y())) {
                boolean shift=me.mrhakan.agalarhack.services.ClientServices.require(me.mrhakan.agalarhack.services.InputStateService.class).keyDown(340)
                        ||me.mrhakan.agalarhack.services.ClientServices.require(me.mrhakan.agalarhack.services.InputStateService.class).keyDown(344);
                selected = id;
                if(shift){if(!selection.add(id))selection.remove(id);return true;}
                if(!selection.contains(id)){selection.clear();selection.add(id);}
                if(AgalarHackClient.HUD_LAYOUT.get(id).locked)return true;
                dragging = id;
                grabX = event.x() - b.x;
                grabY = event.y() - b.y;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (dragging == null || event.button() != 0) {
            return super.mouseDragged(event, dragX, dragY);
        }
        Bounds b = boundsFor(dragging);
        Point snapped = snapPosition(dragging,
                (int) Math.round(event.x() - grabX),
                (int) Math.round(event.y() - grabY),
                b.width, b.height);
        moveSelection(snapped.x-b.x,snapped.y-b.y,false);
        updateBounds();
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragging != null && event.button() == 0) {
            Bounds b = boundsFor(dragging);
            Point snapped = snapPosition(dragging,
                    (int) Math.round(event.x() - grabX),
                    (int) Math.round(event.y() - grabY),
                    b.width, b.height);
            moveSelection(snapped.x-b.x,snapped.y-b.y,true);
            AgalarHackClient.HUD_LAYOUT.save();
            dragging = null;
            updateBounds();
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public void onClose() {
        if (dragging != null) {
            AgalarHackClient.HUD_LAYOUT.save();
            dragging = null;
        }
        minecraft.gui.setScreen(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        updateBounds();
        Set<String> overlaps = overlappingWidgets();
        if(dragging!=null){
            graphics.fill(width/2,0,width/2+1,toolbarTop-6,0x8058a6ff);
            graphics.fill(0,(toolbarTop-6)/2,width,(toolbarTop-6)/2+1,0x8058a6ff);
            Bounds moving=bounds.get(dragging);
            for(var entry:bounds.entrySet())if(!selection.contains(entry.getKey())){
                Bounds other=entry.getValue();
                if(moving.x==other.x)graphics.fill(other.x,0,other.x+1,toolbarTop-6,0x8080ffb0);
                if(moving.y==other.y)graphics.fill(0,other.y,width,other.y+1,0x8080ffb0);
            }
        }

        graphics.centeredText(font, "HUD EDITOR", width / 2, 7, 0xFFFFFFFF);
        graphics.centeredText(font,
                "Drag • grid " + (AgalarHackClient.HUD_LAYOUT.editorOptions().gridVisible ? "ON" : "OFF") + " • snap " + (AgalarHackClient.HUD_LAYOUT.editorOptions().snapping ? "ON" : "OFF")
                        + " • selected " + title(selected),
                width / 2, 20, 0xFF9FB1C7);

        for (String id : widgets()) {
            Bounds b = bounds.get(id);
            WidgetState state = AgalarHackClient.HUD_LAYOUT.get(id);
            boolean active = selection.contains(id);
            boolean collision = overlaps.contains(id);
            int fill = active ? 0x90425E7A : 0x70303A48;
            if (!state.visible) {
                fill = active ? 0x704B3D50 : 0x50252B34;
            }
            int border = collision ? 0xFFFF5E6C : active ? 0xFF74B9FF : 0xFF617185;
            graphics.fill(b.x, b.y, b.x + b.width, b.y + b.height, fill);
            graphics.outline(b.x, b.y, b.width, b.height, border);
            graphics.text(font, title(id) + (state.visible ? "" : " [hidden]")+(state.locked?" [locked]":""), b.x + 5, b.y + 5, 0xFFFFFFFF, true);
            graphics.text(font, state.anchor.name(), b.x + 5, b.y + 5 + font.lineHeight, 0xFFB8C5D6, true);
            if (collision) {
                graphics.text(font, "OVERLAP", b.x + 5, b.y + 5 + font.lineHeight * 2, 0xFFFF8A94, true);
            }
        }

        WidgetState state = AgalarHackClient.HUD_LAYOUT.get(selected);
        String status = state.anchor.name() + "  offset " + state.offsetX + ", " + state.offsetY;
        status += " • grid " + gridSize() + " • magnet " + snapThreshold() + " • margin " + safeMargin();
        if (!overlaps.isEmpty()) {
            status += "  • overlap detected: " + String.join(", ", overlaps);
        }
        graphics.centeredText(font, status, width / 2, toolbarTop - 12,
                overlaps.isEmpty() ? 0xFFB8C5D6 : 0xFFFF8A94);
    }

    private void moveSelection(int dx,int dy,boolean anchor){
        updateBounds();
        int minDx=Integer.MIN_VALUE,maxDx=Integer.MAX_VALUE,minDy=Integer.MIN_VALUE,maxDy=Integer.MAX_VALUE;
        for(String id:selection){if(AgalarHackClient.HUD_LAYOUT.get(id).locked)continue;Bounds b=bounds.get(id);
            minDx=Math.max(minDx,safeMargin()-b.x);maxDx=Math.min(maxDx,width-safeMargin()-b.width-b.x);
            minDy=Math.max(minDy,safeMargin()-b.y);maxDy=Math.min(maxDy,toolbarTop-6-safeMargin()-b.height-b.y);
        }
        if(minDx==Integer.MIN_VALUE)return;
        if(maxDx<minDx){minDx=0;maxDx=0;}
        if(maxDy<minDy){minDy=0;maxDy=0;}
        dx=me.mrhakan.agalarhack.ui.hud.HudGeometry.clamp(dx,minDx,maxDx);dy=me.mrhakan.agalarhack.ui.hud.HudGeometry.clamp(dy,minDy,maxDy);
        for(String id:selection){if(AgalarHackClient.HUD_LAYOUT.get(id).locked)continue;Bounds b=bounds.get(id);
            AgalarHackClient.HUD_LAYOUT.moveTo(id,b.x+dx,b.y+dy,width,height,b.width,b.height,anchor,false);
        }
    }
    @Override public boolean keyPressed(net.minecraft.client.input.KeyEvent event){
        if((event.modifiers()&2)!=0 && event.key()>=262 && event.key()<=265){
            int step=(event.modifiers()&1)!=0?10:1;
            moveSelection(event.key()==262?step:event.key()==263?-step:0,event.key()==264?step:event.key()==265?-step:0,false);
            AgalarHackClient.HUD_LAYOUT.save();return true;
        }
        return super.keyPressed(event);
    }

    private Point snapPosition(String id, int x, int y, int contentWidth, int contentHeight) {
        int maxX = Math.max(0, width - contentWidth);
        int maxY = Math.max(0, toolbarTop - 6 - contentHeight);
        int snappedX = Math.max(0, Math.min(maxX, x));
        int snappedY = Math.max(0, Math.min(maxY, y));
        if (!AgalarHackClient.HUD_LAYOUT.editorOptions().snapping) {
            return new Point(snappedX, snappedY);
        }

        snappedX = Math.max(0, Math.min(maxX, Math.round(snappedX / (float) gridSize()) * gridSize()));
        snappedY = Math.max(0, Math.min(maxY, Math.round(snappedY / (float) gridSize()) * gridSize()));
        updateBounds();

        int bestX = snappedX;
        int bestXDistance = snapThreshold() + 1;
        int bestY = snappedY;
        int bestYDistance = snapThreshold() + 1;
        int[] ownX = {0, contentWidth / 2, contentWidth};
        int[] ownY = {0, contentHeight / 2, contentHeight};

        for (Map.Entry<String, Bounds> entry : bounds.entrySet()) {
            if (selection.contains(entry.getKey()) || !AgalarHackClient.HUD_LAYOUT.get(entry.getKey()).visible) {
                continue;
            }
            Bounds other = entry.getValue();
            int[] targetsX = {other.x, other.x + other.width / 2, other.x + other.width};
            int[] targetsY = {other.y, other.y + other.height / 2, other.y + other.height};
            for (int ownOffset : ownX) {
                for (int target : targetsX) {
                    int candidate = target - ownOffset;
                    int distance = Math.abs(candidate - snappedX);
                    if (distance <= snapThreshold() && distance < bestXDistance) {
                        bestXDistance = distance;
                        bestX = candidate;
                    }
                }
            }
            for (int ownOffset : ownY) {
                for (int target : targetsY) {
                    int candidate = target - ownOffset;
                    int distance = Math.abs(candidate - snappedY);
                    if (distance <= snapThreshold() && distance < bestYDistance) {
                        bestYDistance = distance;
                        bestY = candidate;
                    }
                }
            }
        }
        if(Math.abs(bestX+contentWidth/2-width/2)<=snapThreshold())bestX=width/2-contentWidth/2;
        if(Math.abs(bestY+contentHeight/2-(toolbarTop-6)/2)<=snapThreshold())bestY=(toolbarTop-6)/2-contentHeight/2;
        return new Point(Math.max(safeMargin(), Math.min(maxX-safeMargin(), bestX)), Math.max(safeMargin(), Math.min(maxY-safeMargin(), bestY)));
    }

    private Set<String> overlappingWidgets() {
        Set<String> overlaps = new LinkedHashSet<>();
        for (int i = 0; i < widgets().size(); i++) {
            Bounds a = bounds.get(widgets().get(i));
            for (int j = i + 1; j < widgets().size(); j++) {
                Bounds b = bounds.get(widgets().get(j));
                if (AgalarHackClient.HUD_LAYOUT.get(widgets().get(i)).visible && AgalarHackClient.HUD_LAYOUT.get(widgets().get(j)).visible && a != null && b != null && a.intersects(b)) {
                    overlaps.add(widgets().get(i));
                    overlaps.add(widgets().get(j));
                }
            }
        }
        return overlaps;
    }

    private void updateBounds() {
        widgetOrder=me.mrhakan.agalarhack.services.ClientServices.require(me.mrhakan.agalarhack.ui.hud.HudRegistry.class).ids();
        bounds.clear();
        for (String id : widgets()) {
            bounds.put(id, boundsFor(id));
        }
    }

    private Bounds boundsFor(String id) {
        var component=me.mrhakan.agalarhack.services.ClientServices.require(me.mrhakan.agalarhack.ui.hud.HudRegistry.class).get(id);
        int w=Math.max(70,Math.min(width,component.width().getAsInt()));
        int h=Math.max(30,Math.min(toolbarTop-6,component.height().getAsInt()));
        int x = AgalarHackClient.HUD_LAYOUT.resolveX(id, width, w);
        int y = AgalarHackClient.HUD_LAYOUT.resolveY(id, height, h);
        return new Bounds(x, y, w, h);
    }

    private String title(String id) {
        var component=me.mrhakan.agalarhack.services.ClientServices.require(me.mrhakan.agalarhack.ui.hud.HudRegistry.class).get(id);
        return component==null?id:component.title();
    }

    private record Point(int x, int y) {
    }

    private record Bounds(int x, int y, int width, int height) {
        boolean contains(double px, double py) {
            return px >= x && px <= x + width && py >= y && py <= y + height;
        }

        boolean intersects(Bounds other) {
            return me.mrhakan.agalarhack.ui.hud.HudGeometry.overlaps(x,y,width,height,other.x,other.y,other.width,other.height);
        }
    }
}
