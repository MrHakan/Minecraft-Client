package me.mrhakan.agalarhack.ui;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import me.mrhakan.agalarhack.AgalarHackClient;
import me.mrhakan.agalarhack.module.Module;
import me.mrhakan.agalarhack.ui.hud.HudInfoProvider;
import me.mrhakan.agalarhack.ui.hud.HudLine;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.util.ARGB;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class Hud implements HudElement {

    private static final EquipmentSlot[] TARGET_EQUIPMENT = {
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET,
            EquipmentSlot.MAINHAND,
            EquipmentSlot.OFFHAND
    };

    private final me.mrhakan.agalarhack.ui.hud.HudRegistry registry = me.mrhakan.agalarhack.services.ClientServices.require(me.mrhakan.agalarhack.ui.hud.HudRegistry.class);
    public Hud() {
        new me.mrhakan.agalarhack.ui.hud.ScannerDebugHud(
                me.mrhakan.agalarhack.services.ClientServices.require(me.mrhakan.agalarhack.services.ScannerService.class),
                AgalarHackClient.HUD_LAYOUT, AgalarHackClient.moduleManager).register(registry);
        new me.mrhakan.agalarhack.ui.hud.ModuleTimingsHud(
                me.mrhakan.agalarhack.services.ClientServices.require(me.mrhakan.agalarhack.services.ModuleTimings.class),
                AgalarHackClient.HUD_LAYOUT).register(registry);
        register("branding","Branding",()->Minecraft.getInstance().font.width(AgalarHackClient.NAME+" "+AgalarHackClient.VERSION)+5,()->Minecraft.getInstance().font.lineHeight,this::renderBranding);
        var moduleList = new me.mrhakan.agalarhack.ui.hud.ModuleListHud();
        register("modules", "Module List", moduleList::width, moduleList::height, moduleList::render);
        register("info","Info",()->150,()->48,this::renderInfo);
        register("target","Target HUD",()->155,()->120,this::renderTarget);
        textComponent("fps","FPS",()->"FPS "+Minecraft.getInstance().getFps());
        textComponent("memory","Memory",()->"Memory "+(Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory())/(1024*1024)+" MiB");
        textComponent("server","Server",()->me.mrhakan.agalarhack.services.ClientServices.require(me.mrhakan.agalarhack.services.ServerContextService.class).address(Minecraft.getInstance()));
        textComponent("speed","Speed",Hud::speedLine);
        register("movement","Movement Stats",()->132,()->48,this::renderMovementStats);
        textComponent("waypoint","Waypoint",()->nearestWaypointLine());
        textComponent("tps","TPS (estimate)",()->serverInfoLine());
        registry.register(new me.mrhakan.agalarhack.ui.hud.HudRegistry.Component("ping_graph","Ping Graph",()->104,()->34,event->renderPingGraph(event.graphics())),
                new me.mrhakan.agalarhack.managers.HudLayoutManager.WidgetState(me.mrhakan.agalarhack.managers.HudLayoutManager.Anchor.TOP_LEFT,8,150,false));
        textComponent("direction","Direction",()->Minecraft.getInstance().player==null?"Facing --":"Facing "+Minecraft.getInstance().player.getDirection());
        registry.register(new me.mrhakan.agalarhack.ui.hud.HudRegistry.Component(
                        "inventory", "Inventory", () -> INVENTORY_WIDTH, () -> INVENTORY_HEIGHT,
                        event -> renderInventory(event.graphics(), Minecraft.getInstance())),
                new me.mrhakan.agalarhack.managers.HudLayoutManager.WidgetState(
                        me.mrhakan.agalarhack.managers.HudLayoutManager.Anchor.BOTTOM_RIGHT, 8, 110, false));
    }
    private static final int SLOT = 18;
    private static final int INVENTORY_WIDTH = 9 * SLOT + 8;
    /** Equipment row, a separator, then the three storage rows. */
    private static final int INVENTORY_HEIGHT = 4 * SLOT + 14;
    /** Armour top to bottom, then the offhand, which is how the inventory screen reads. */
    private static final EquipmentSlot[] WORN = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.OFFHAND
    };

    /**
     * The 27 storage slots, with worn equipment above them.
     *
     * <p>The hotbar is deliberately absent: vanilla already draws it, and repeating it would waste a
     * row of a widget whose whole point is showing what vanilla does not.
     *
     * <p>Decorations come from vanilla's own {@code itemDecorations}, so stack counts, durability
     * bars and cooldown sweeps all match the real inventory screen instead of being approximated.
     */
    private void renderInventory(GuiGraphicsExtractor g, Minecraft client) {
        // The HUD can be asked to measure or draw before a world exists.
        if (client.player == null) return;
        int x = AgalarHackClient.HUD_LAYOUT.resolveX("inventory", g.guiWidth(), INVENTORY_WIDTH);
        int y = AgalarHackClient.HUD_LAYOUT.resolveY("inventory", g.guiHeight(), INVENTORY_HEIGHT);
        g.fill(x, y, x + INVENTORY_WIDTH, y + INVENTORY_HEIGHT, 0xC8101620);

        int wornY = y + 4;
        for (int index = 0; index < WORN.length; index++) {
            slot(g, client, client.player.getItemBySlot(WORN[index]), x + 4 + index * SLOT, wornY);
        }
        // Separator rather than a gap: without it the equipment row reads as part of the storage grid.
        int ruleY = wornY + SLOT + 2;
        g.fill(x + 4, ruleY, x + INVENTORY_WIDTH - 4, ruleY + 1, 0x40FFFFFF);

        int storageY = ruleY + 4;
        for (int index = 9; index < 36; index++) {
            slot(g, client, client.player.getInventory().getItem(index),
                    x + 4 + (index - 9) % 9 * SLOT, storageY + (index - 9) / 9 * SLOT);
        }
    }

    /** An empty slot still gets its outline, so the grid stays readable when the pack is half full. */
    private void slot(GuiGraphicsExtractor g, Minecraft client, ItemStack item, int x, int y) {
        g.fill(x, y, x + SLOT - 2, y + SLOT - 2, 0x30FFFFFF);
        if (item.isEmpty()) return;
        g.item(item, x, y);
        g.itemDecorations(client.font, item, x, y);
    }

    private static me.mrhakan.agalarhack.services.MovementStats movementStats() {
        return me.mrhakan.agalarhack.services.ClientServices.require(me.mrhakan.agalarhack.services.MovementStats.class);
    }

    /**
     * Measured from where the player ended up, not from the motion vector, which a collision zeroes
     * the moment you scrape a wall - exactly when the reading is being watched.
     */
    private static String speedLine() {
        var stats=movementStats();
        return stats.hasSamples()?String.format(Locale.ROOT,"Speed %.2f b/s",stats.horizontalSpeed()):"Speed --";
    }

    /** Horizontal and vertical speed, recent average and peak, plus acceleration while it is changing. */
    private void renderMovementStats(GuiGraphicsExtractor g, Minecraft client) {
        var stats=movementStats();
        var font=client.font;
        List<String> lines=new ArrayList<>(4);
        if(!stats.hasSamples()) lines.add("Movement --");
        else{
            lines.add(String.format(Locale.ROOT,"H %.2f b/s  V %+.2f",stats.horizontalSpeed(),stats.verticalSpeed()));
            lines.add(String.format(Locale.ROOT,"avg %.2f  peak %.2f",stats.averageHorizontalSpeed(),stats.peakHorizontalSpeed()));
            lines.add(String.format(Locale.ROOT,"accel %+.1f b/s2",stats.horizontalAcceleration()));
            double fall=stats.peakFallSpeed();
            lines.add(fall>0?String.format(Locale.ROOT,"peak fall %.2f b/s",fall):"peak fall --");
        }
        int width=Math.max(132,lines.stream().mapToInt(font::width).max().orElse(0)+10);
        int height=lines.size()*(font.lineHeight+2)+6;
        int x=AgalarHackClient.HUD_LAYOUT.resolveX("movement",g.guiWidth(),width);
        int y=AgalarHackClient.HUD_LAYOUT.resolveY("movement",g.guiHeight(),height);
        g.fill(x,y,x+width,y+height,ClientUiTheme.PANEL);
        for(int index=0;index<lines.size();index++){
            g.text(font,lines.get(index),x+5,y+3+index*(font.lineHeight+2),ClientUiTheme.TEXT,true);
        }
    }

    private static me.mrhakan.agalarhack.module.misc.ServerInfo serverInfo() {
        var module=AgalarHackClient.moduleManager.getModule("ServerInfo");
        return module instanceof me.mrhakan.agalarhack.module.misc.ServerInfo info && info.isToggled()?info:null;
    }

    /** Always labelled as an estimate: the client is never told the server's real tick rate. */
    private static String serverInfoLine() {
        var info=serverInfo();
        if(info==null||!info.tickEstimate().hasEstimate()) return "TPS --";
        var ticks=info.tickEstimate();
        return String.format(Locale.ROOT,"~%.1f tps (est, min %.1f)",ticks.average(),ticks.minimum());
    }

    private static void renderPingGraph(GuiGraphicsExtractor g) {
        var info=serverInfo();
        int x=AgalarHackClient.HUD_LAYOUT.resolveX("ping_graph",g.guiWidth(),104);
        int y=AgalarHackClient.HUD_LAYOUT.resolveY("ping_graph",g.guiHeight(),34);
        g.fill(x,y,x+104,y+34,0xc8101620);
        var mc=Minecraft.getInstance();
        if(info==null){g.text(mc.font,"Ping --",x+4,y+4,ClientUiTheme.TEXT,true);return;}
        var samples=info.pingSamples().snapshot();
        if(samples.length==0){g.text(mc.font,"Ping --",x+4,y+4,ClientUiTheme.TEXT,true);return;}
        double maximum=Math.max(50,info.pingSamples().maximum());
        int plotTop=y+14,plotBottom=y+32;
        // One column per sample, newest on the right; the bar height is relative to the window maximum.
        int columns=Math.min(100,samples.length);
        for(int i=0;i<columns;i++){
            double value=samples[samples.length-columns+i];
            int height=(int)Math.round((plotBottom-plotTop)*Math.min(1.0,value/maximum));
            int color=value>300?0xffff5555:value>150?0xffffcc55:0xff55ff88;
            g.fill(x+2+i,plotBottom-height,x+3+i,plotBottom,color);
        }
        g.text(mc.font,String.format(Locale.ROOT,"Ping %d ms",Math.round(info.pingSamples().latest())),x+4,y+3,ClientUiTheme.TEXT,true);
    }

    /** Nearest visible waypoint in this dimension, with an arrow relative to where the player looks. */
    private static String nearestWaypointLine() {
        var mc=Minecraft.getInstance();
        if(mc.player==null||mc.level==null) return "Waypoint --";
        var module=AgalarHackClient.moduleManager.getModule("Waypoints");
        if(module==null||!module.isToggled()) return "Waypoint --";
        String dimension=me.mrhakan.agalarhack.module.render.Waypoints.currentDimension(mc);
        var service=me.mrhakan.agalarhack.services.ClientServices.registry()==null?null
                :me.mrhakan.agalarhack.services.ClientServices.registry()
                        .find(me.mrhakan.agalarhack.services.WaypointService.class).orElse(null);
        if(service==null||dimension==null) return "Waypoint --";
        me.mrhakan.agalarhack.services.Waypoint nearest=null;
        double best=Double.MAX_VALUE;
        for(var point:service.visibleIn(dimension)){
            double distance=point.horizontalDistanceTo(mc.player.getX(),mc.player.getZ());
            if(distance<best){best=distance;nearest=point;}
        }
        if(nearest==null) return "Waypoint --";
        char arrow=me.mrhakan.agalarhack.services.WaypointCompass.arrow(
                me.mrhakan.agalarhack.services.WaypointCompass.relativeBearing(
                        mc.player.getX(),mc.player.getZ(),nearest.x()+0.5,nearest.z()+0.5,mc.player.getYRot()));
        return arrow+" "+nearest.name()+" "+Math.round(best)+"m";
    }

    private void register(String id,String title,java.util.function.IntSupplier width,java.util.function.IntSupplier height,java.util.function.BiConsumer<GuiGraphicsExtractor,Minecraft> render) {
        registry.register(new me.mrhakan.agalarhack.ui.hud.HudRegistry.Component(id,title,width,height,event->render.accept(event.graphics(),Minecraft.getInstance())),AgalarHackClient.HUD_LAYOUT.get(id).copy());
    }
    private void textComponent(String id,String title,java.util.function.Supplier<String> text) {
        registry.register(new me.mrhakan.agalarhack.ui.hud.HudRegistry.Component(id,title,()->Minecraft.getInstance().font.width(text.get()),()->Minecraft.getInstance().font.lineHeight,event->{
            var g=event.graphics();var font=Minecraft.getInstance().font;String value=text.get();
            g.text(font,value,AgalarHackClient.HUD_LAYOUT.resolveX(id,g.guiWidth(),font.width(value)),AgalarHackClient.HUD_LAYOUT.resolveY(id,g.guiHeight(),font.lineHeight),ClientUiTheme.TEXT,true);
        }),new me.mrhakan.agalarhack.managers.HudLayoutManager.WidgetState(me.mrhakan.agalarhack.managers.HudLayoutManager.Anchor.TOP_LEFT,8,90+registry.ids().size()*12,false));
    }

    public static class ModuleComparator implements Comparator<Module> {
        @Override
        public int compare(Module a, Module b) {
            Font font = Minecraft.getInstance().font;
            return Integer.compare(font.width(b.getDisplayName()), font.width(a.getDisplayName()));
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || AgalarHackClient.moduleManager == null) {
            return;
        }
        registry.render(new me.mrhakan.agalarhack.events.ClientEvents.HudRender(graphics,deltaTracker));
    }

    private void renderBranding(GuiGraphicsExtractor graphics, Minecraft mc) {
        if (!AgalarHackClient.HUD_LAYOUT.get("branding").visible) {
            return;
        }
        Font font = mc.font;
        String text = AgalarHackClient.NAME + " " + AgalarHackClient.VERSION;
        int x = AgalarHackClient.HUD_LAYOUT.resolveX("branding", graphics.guiWidth(), font.width(text));
        int y = AgalarHackClient.HUD_LAYOUT.resolveY("branding", graphics.guiHeight(), font.lineHeight);
        graphics.text(font, AgalarHackClient.NAME, x, y, rainbow(0), true);
        graphics.text(font, AgalarHackClient.VERSION, x + font.width(AgalarHackClient.NAME) + 5, y, 0xFFFFFACD, true);
    }

    private void renderInfo(GuiGraphicsExtractor graphics, Minecraft mc) {
        if (!AgalarHackClient.HUD_LAYOUT.get("info").visible) {
            return;
        }
        Font font = mc.font;
        List<HudLine> lines = new ArrayList<>();
        for (Module mod : AgalarHackClient.moduleManager.getModuleList()) {
            if (mod.isToggled() && mod instanceof HudInfoProvider provider) {
                lines.addAll(provider.getHudLines());
            }
        }
        if (lines.isEmpty()) {
            return;
        }
        int width = lines.stream().mapToInt(line -> font.width(line.text())).max().orElse(0);
        int height = lines.size() * font.lineHeight;
        int x = AgalarHackClient.HUD_LAYOUT.resolveX("info", graphics.guiWidth(), width);
        int y = AgalarHackClient.HUD_LAYOUT.resolveY("info", graphics.guiHeight(), height);
        for (HudLine line : lines) {
            graphics.text(font, line.text(), x, y, line.color(), true);
            y += font.lineHeight;
        }
    }

    /** Eased bar state; reset when the target changes so the bar never slides between players. */
    private double displayedHealth;
    private String barTargetName;

    /**
     * Face plus hat layer, from the skin the client already has for that player.
     *
     * <p>The hat layer is not optional in practice: skipping it leaves a bald head for anyone whose
     * skin puts hair, a hood or glasses on it, which is most of them.
     */
    private void renderFace(GuiGraphicsExtractor graphics, net.minecraft.client.player.AbstractClientPlayer player, int x, int y) {
        var skin = player.getSkin();
        if (skin == null || skin.body() == null) return;
        var texture = skin.body().texturePath();
        int size = me.mrhakan.agalarhack.ui.hud.PlayerFace.DRAWN_SIZE;
        int patch = me.mrhakan.agalarhack.ui.hud.PlayerFace.PATCH;
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y,
                me.mrhakan.agalarhack.ui.hud.PlayerFace.FACE_U, me.mrhakan.agalarhack.ui.hud.PlayerFace.FACE_V,
                size, size, patch, patch,
                me.mrhakan.agalarhack.ui.hud.PlayerFace.SKIN_WIDTH, me.mrhakan.agalarhack.ui.hud.PlayerFace.SKIN_HEIGHT,
                ARGB.white(1.0f));
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y,
                me.mrhakan.agalarhack.ui.hud.PlayerFace.HAT_U, me.mrhakan.agalarhack.ui.hud.PlayerFace.HAT_V,
                size, size, patch, patch,
                me.mrhakan.agalarhack.ui.hud.PlayerFace.SKIN_WIDTH, me.mrhakan.agalarhack.ui.hud.PlayerFace.SKIN_HEIGHT,
                ARGB.white(1.0f));
    }

    private void renderTarget(GuiGraphicsExtractor graphics, Minecraft mc) {
        Module targetHud = AgalarHackClient.moduleManager.getModule("TargetHUD");
        if (targetHud == null || !targetHud.isToggled() || !AgalarHackClient.HUD_LAYOUT.get("target").visible) {
            return;
        }
        double timeout = targetHud.getNumberSetting("timeout", 3.0);
        LivingEntity target = AgalarHackClient.TARGET_TRACKER.get(timeout);
        if (target == null) {
            return;
        }

        Font font = mc.font;
        var layout = me.mrhakan.agalarhack.ui.hud.TargetHudModel.Layout.parse(targetHud.getStringSetting("layout", "compact"));
        boolean healthBar = targetHud.getBooleanSetting("healthBar", true);
        boolean showEquipment = targetHud.getBooleanSetting("showEquipment", true)
                && me.mrhakan.agalarhack.ui.hud.TargetHudModel.showsIcons(layout);
        boolean showEffects = targetHud.getBooleanSetting("showEffects", true)
                && me.mrhakan.agalarhack.ui.hud.TargetHudModel.showsIcons(layout);

        boolean friend = targetHud.getBooleanSetting("friendMarker", true) && target instanceof Player
                && AgalarHackClient.FRIEND_MANAGER != null
                && AgalarHackClient.FRIEND_MANAGER.isFriend(target.getName().getString());
        int ping = -1;
        if (target instanceof Player && mc.getConnection() != null) {
            var info = mc.getConnection().getPlayerInfo(target.getUUID());
            if (info != null) ping = info.getLatency();
        }
        var model = new me.mrhakan.agalarhack.ui.hud.TargetHudModel.Target(
                target.getName().getString(), target.getHealth(), target.getMaxHealth(),
                target.getAbsorptionAmount(), mc.player.distanceTo(target), target.getArmorValue(),
                ping, target instanceof Player, friend,
                targetHud.getBooleanSetting("hurtIndicator", true) && target.hurtTime > 0);

        List<String> lines = me.mrhakan.agalarhack.ui.hud.TargetHudModel.lines(layout, model,
                targetHud.getBooleanSetting("showHealth", true),
                targetHud.getBooleanSetting("showDistance", true),
                targetHud.getBooleanSetting("showArmor", true));

        List<ItemStack> equipment = new ArrayList<>();
        if (showEquipment) {
            for (EquipmentSlot slot : TARGET_EQUIPMENT) {
                ItemStack item = target.getItemBySlot(slot);
                if (!item.isEmpty()) {
                    equipment.add(item);
                }
            }
        }

        List<MobEffectInstance> effects = new ArrayList<>();
        if (showEffects) {
            int limit = (int) Math.round(targetHud.getNumberSetting("maxEffects", 6.0));
            for (MobEffectInstance effect : target.getActiveEffects()) {
                if (effects.size() >= limit) {
                    break;
                }
                effects.add(effect);
            }
        }

        // Only players have a skin, so a mob card keeps exactly its previous layout.
        boolean showFace = targetHud.getBooleanSetting("showFace", true)
                && target instanceof net.minecraft.client.player.AbstractClientPlayer;
        int indent = me.mrhakan.agalarhack.ui.hud.PlayerFace.textIndent(showFace);

        int contentWidth = lines.stream().mapToInt(font::width).max().orElse(80) + indent;
        int iconWidth = Math.max(equipment.size(), effects.size()) * 18;
        int boxWidth = Math.max(132, Math.max(contentWidth + 12, iconWidth + 12));
        int textHeight = Math.max(lines.size() * font.lineHeight,
                me.mrhakan.agalarhack.ui.hud.PlayerFace.minimumContentHeight(showFace));
        int boxHeight = textHeight + 10;
        if (healthBar) {
            boxHeight += 7;
        }
        if (!equipment.isEmpty()) {
            boxHeight += 18;
        }
        if (!effects.isEmpty()) {
            boxHeight += 20;
        }

        int x = AgalarHackClient.HUD_LAYOUT.resolveX("target", graphics.guiWidth(), boxWidth);
        int y = AgalarHackClient.HUD_LAYOUT.resolveY("target", graphics.guiHeight(), boxHeight);
        graphics.fill(x, y, x + boxWidth, y + boxHeight, 0xB0101010);
        graphics.fill(x, y, x + 3, y + boxHeight, 0xFF55AAFF);

        int cursorY = y + 5;
        if (showFace) renderFace(graphics, (net.minecraft.client.player.AbstractClientPlayer) target, x + 7, cursorY);
        for (int i = 0; i < lines.size(); i++) {
            graphics.text(font, lines.get(i), x + 7 + indent, cursorY, i == 0 ? 0xFFFFFFFF : 0xFFDDDDDD, true);
            cursorY += font.lineHeight;
        }
        // The bar and icons below start under whichever is taller, the text or the face.
        cursorY = Math.max(cursorY, y + 5 + me.mrhakan.agalarhack.ui.hud.PlayerFace.minimumContentHeight(showFace));

        if (healthBar) {
            double actual = me.mrhakan.agalarhack.ui.hud.TargetHudModel.healthFraction(model);
            // Reduced motion and the theme's animation switch both override the module's own toggle.
            boolean animate = targetHud.getBooleanSetting("animateHealth", true)
                    && me.mrhakan.agalarhack.services.ClientServices.registry()
                        .find(me.mrhakan.agalarhack.services.ThemeService.class)
                        .map(themes -> themes.current().motionEnabled()).orElse(true);
            if (!target.getName().getString().equals(barTargetName)) {
                // A new target starts from its true value rather than sliding up from the old one.
                barTargetName = target.getName().getString();
                displayedHealth = actual;
            }
            displayedHealth = me.mrhakan.agalarhack.ui.hud.TargetHudModel.easeToward(displayedHealth, actual,
                    targetHud.getNumberSetting("animationSpeed", 0.25), animate);
            int barX = x + 7;
            int barWidth = boxWidth - 14;
            graphics.fill(barX, cursorY + 1, barX + barWidth, cursorY + 5, 0xFF333333);
            int filled = (int) Math.round(barWidth * displayedHealth);
            int barColor = me.mrhakan.agalarhack.ui.hud.TargetHudModel.barColor(actual, model.hurt());
            if (filled > 0) {
                graphics.fill(barX, cursorY + 1, barX + filled, cursorY + 5, barColor);
            }
            cursorY += 7;
        }

        if (!equipment.isEmpty()) {
            int itemX = x + 7;
            for (ItemStack item : equipment) {
                graphics.item(item, itemX, cursorY);
                itemX += 18;
            }
            cursorY += 18;
        }

        if (!effects.isEmpty()) {
            int effectX = x + 7;
            for (MobEffectInstance effect : effects) {
                graphics.blitSprite(
                        RenderPipelines.GUI_TEXTURED,
                        net.minecraft.client.gui.Hud.getMobEffectSprite(effect.getEffect()),
                        effectX,
                        cursorY + 1,
                        18,
                        18,
                        ARGB.white(1.0f));
                effectX += 18;
            }
        }
    }

    public static int rainbow(int delay) {
        var themes = me.mrhakan.agalarhack.services.ClientServices.registry().find(me.mrhakan.agalarhack.services.ThemeService.class);
        if (themes.isPresent() && (!themes.get().current().motionEnabled() || themes.get().current().highContrast))
            return ClientUiTheme.ACCENT;
        double rainbowState = Math.ceil((System.currentTimeMillis() + delay) / 25.0);
        rainbowState %= 360;
        return Color.getHSBColor((float) (rainbowState / 360.0f), 1f, 1f).getRGB();
    }
}
