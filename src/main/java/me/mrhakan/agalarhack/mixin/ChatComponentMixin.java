package me.mrhakan.agalarhack.mixin;

import me.mrhakan.agalarhack.events.ChatHooks;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Rewrites a chat line just before it is stored for display.
 *
 * <p>Fabric 26.2 has {@code ALLOW_CHAT} and {@code CHAT}, which can veto a message or observe it, but
 * nothing that changes what is drawn. The private {@code addMessage} is the single point every added
 * message reaches — the three public {@code add*} methods all delegate to it — so decorating there
 * covers player chat, server system messages and the client's own without touching three call sites.
 *
 * <p>{@code argsOnly} with ordinal 0 targets the {@code Component} parameter itself, so this replaces
 * what the method was given rather than injecting code into its body. That is both the smallest
 * possible change and the one least likely to break on a Minecraft update, since it depends on the
 * signature rather than on anything inside the method.
 *
 * <p>The hook returns the message unchanged if anything at all goes wrong. Chat that fails to draw is
 * far worse than chat without a timestamp.
 */
@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {
    @ModifyVariable(method = "addMessage(Lnet/minecraft/network/chat/Component;"
            + "Lnet/minecraft/network/chat/MessageSignature;"
            + "Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;"
            + "Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Component agalarhack$decorate(Component message) {
        return ChatHooks.decorate(message);
    }
}
