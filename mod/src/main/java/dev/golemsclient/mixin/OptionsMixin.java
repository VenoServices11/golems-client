package dev.golemsclient.mixin;

import dev.golemsclient.lobby.Lobby;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The lobby scene is a few chunks wide: a short view distance lets the fog hide where it ends. */
@Mixin(Options.class)
public abstract class OptionsMixin {
    @Inject(method = "getEffectiveRenderDistance", at = @At("RETURN"), cancellable = true)
    private void golemsclient$lobbyDistance(CallbackInfoReturnable<Integer> cir) {
        if (Lobby.active() || Lobby.busy() && Lobby.phase() == Lobby.Phase.LOADING) cir.setReturnValue(Math.min(cir.getReturnValue(), 6));
    }
}
