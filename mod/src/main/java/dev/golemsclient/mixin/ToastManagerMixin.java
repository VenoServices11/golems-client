package dev.golemsclient.mixin;

import dev.golemsclient.lobby.Lobby;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.ToastManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** No "recipe unlocked" and similar pop-ups from the lobby world. */
@Mixin(ToastManager.class)
public abstract class ToastManagerMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void golemsclient$hideInLobby(GuiGraphics g, CallbackInfo ci) {
        if (Lobby.active()) ci.cancel();
    }
}
