package dev.golemsclient.mixin;

import dev.golemsclient.lobby.Lobby;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** No hotbar, hearts or crosshair in the lobby. */
@Mixin(Gui.class)
public abstract class GuiMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void golemsclient$hideInLobby(GuiGraphics g, DeltaTracker delta, CallbackInfo ci) {
        if (Lobby.active()) ci.cancel();
    }
}
