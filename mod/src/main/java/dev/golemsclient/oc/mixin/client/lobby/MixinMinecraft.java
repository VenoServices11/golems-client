// Ported from OneClient's PolyPlus (https://github.com/Polyfrost/PolyPlus), GPL-3.0, by Polyfrost.
// Changed for Golem's Client.
package dev.golemsclient.oc.mixin.client.lobby;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.main.GameConfig;
import net.minecraft.client.multiplayer.ClientLevel;
import dev.golemsclient.oc.client.lobby.LobbyCapture;
import dev.golemsclient.oc.client.lobby.LobbyScene;
import dev.golemsclient.oc.client.lobby.StartupOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MixinMinecraft {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void golems$noteQuickPlay(GameConfig config, CallbackInfo ci) {
        StartupOverlay.noteQuickPlay(config.quickPlay.isEnabled());
    }

    @Inject(method = "runTick", at = @At("HEAD"))
    private void golems$stepLobbyClock(boolean advanceGameTime, CallbackInfo ci) {
        LobbyScene.onFrameStart();
    }

    @ModifyExpressionValue(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Screen;isPauseScreen()Z"))
    
    private boolean golems$holdPause(boolean original) {
        return original;
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void golems$tickLobby(CallbackInfo ci) {
        LobbyScene.onClientTick();
        LobbyCapture.onClientTick();
    }

    @Inject(method = "setLevel", at = @At("HEAD"))
    private void golems$releaseLobby(ClientLevel level, CallbackInfo ci) {
        LobbyScene.stop();
    }

    @Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;ZZ)V", at = @At("HEAD"))
    private void golems$captureOnLeave(Screen screen, boolean keepResourcePacks, boolean stopSound, CallbackInfo ci) {
        if (((Minecraft) (Object) this).isRunning()) LobbyCapture.onLeave();
    }

    @Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;ZZ)V", at = @At("RETURN"))
    private void golems$landLeaveCapture(Screen screen, boolean keepResourcePacks, boolean stopSound, CallbackInfo ci) {
        LobbyCapture.drain();
    }

    @Inject(method = "updateLevelInEngines(Lnet/minecraft/client/multiplayer/ClientLevel;Z)V", at = @At("HEAD"))
    private void golems$releaseLobbyEngines(ClientLevel level, boolean stopSound, CallbackInfo ci) {
        if (level == null) LobbyScene.stop();
    }

    @Inject(method = "pauseGame", at = @At("HEAD"), cancellable = true)
    private void golems$noLobbyPause(CallbackInfo ci) {
        if (LobbyScene.terrainActive()) ci.cancel();
    }
}
