package dev.golemsclient.mixin;

import dev.golemsclient.lobby.Captures;
import dev.golemsclient.lobby.Lobby;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Leaving a world: a server's spot may become a lobby scene first; and when the lobby is left straight for a server or
 * a world, its connection is closed properly so its little server stops (Minecraft only expects that from a menu).
 */
@Mixin(Minecraft.class)
public abstract class MinecraftDisconnectMixin {
    @Shadow
    public ClientLevel level;

    @Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;ZZ)V", at = @At("HEAD"))
    private void golemsclient$beforeLeaving(Screen screen, boolean keepResourcePacks, boolean stopSound, CallbackInfo ci) {
        Captures.beforeLeaving((Minecraft) (Object) this);
        if (Lobby.active()) {
            Lobby.leaving(true);
            if (level != null) level.disconnect(ClientLevel.DEFAULT_QUIT_MESSAGE);
        }
    }

    @Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;ZZ)V", at = @At("RETURN"))
    private void golemsclient$afterLeaving(Screen screen, boolean keepResourcePacks, boolean stopSound, CallbackInfo ci) {
        Lobby.leaving(false);
    }
}
