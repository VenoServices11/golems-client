package dev.golemsclient.mixin;

import dev.golemsclient.lobby.Captures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Right before leaving a server: its spot may become a lobby scene. */
@Mixin(Minecraft.class)
public abstract class MinecraftDisconnectMixin {
    @Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;ZZ)V", at = @At("HEAD"))
    private void golemsclient$capture(Screen screen, boolean keepResourcePacks, boolean stopSound, CallbackInfo ci) {
        Captures.beforeLeaving((Minecraft) (Object) this);
    }
}
