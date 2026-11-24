// Ported from OneClient's PolyPlus (https://github.com/Polyfrost/PolyPlus), GPL-3.0, by Polyfrost.
// Changed for Golem's Client.
package dev.golemsclient.oc.mixin.client.lobby;

import net.minecraft.client.renderer.GameRenderer;
import dev.golemsclient.oc.client.lobby.LobbyScene;
import dev.golemsclient.oc.client.lobby.LobbySky;
import dev.golemsclient.oc.mixin.client.access.GameRendererAccessor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class MixinGameRenderer {

    @Inject(method = "render", at = @At("HEAD"))
    private void golems$beginLobbyRender(CallbackInfo ci) {
        LobbyScene.onRenderHead();
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/fog/FogRenderer;endFrame()V"))
    private void golems$endLobbyRender(CallbackInfo ci) {
        LobbyScene.onLevelRendered();
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void golems$captureLobbyFrame(CallbackInfo ci) {
        LobbyScene.onLevelRendered();
    }
    


    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;renderLevel", shift = At.Shift.AFTER), require = 0)
    
    private void golems$processLobbyPost(CallbackInfo ci) {
        golems$lobbyPostDone = true;
        golems$lobbyPost();
    }

    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void golems$processLobbyPostFallback(CallbackInfo ci) {
        if (!golems$lobbyPostDone) golems$lobbyPost();
        golems$lobbyPostDone = false;
    }

    @Unique
    private boolean golems$lobbyPostDone;

    @Unique
    private void golems$lobbyPost() {
        GameRenderer renderer = (GameRenderer) (Object) this;
        if (!LobbyScene.terrainActive()) return;
        LobbyScene.drawHolograms(renderer);
        LobbyScene.onLevelDrawn(renderer, true);
        LobbySky.INSTANCE.process(renderer, ((GameRendererAccessor) renderer).golems$resourcePool());
        
    }

    @Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
    private void golems$hideLobbyHand(CallbackInfo ci) {
        if (LobbyScene.terrainActive()) ci.cancel();
    }

    
}
