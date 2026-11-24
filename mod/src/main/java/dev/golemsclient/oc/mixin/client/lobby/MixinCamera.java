// Ported from OneClient's PolyPlus (https://github.com/Polyfrost/PolyPlus), GPL-3.0, by Polyfrost.
// Changed for Golem's Client.
package dev.golemsclient.oc.mixin.client.lobby;

import dev.golemsclient.oc.client.gui.pausescreen.PauseStage;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.renderer.GameRenderer;

import dev.golemsclient.oc.client.lobby.LobbyScene;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = GameRenderer.class, priority = 2000)

public class MixinCamera {
    @ModifyReturnValue(method = "getFov", at = @At("RETURN"))
    
    private float golems$lobbyFov(float original) {
        return PauseStage.fov(LobbyScene.cameraFov(original));
    }

    @Inject(method = "getFov", at = @At("HEAD"), cancellable = true)
    
    private void golems$holdLobbyFov(CallbackInfoReturnable<Float> cir) {
        if (LobbyScene.terrainActive()) cir.setReturnValue(PauseStage.fov(LobbyScene.cameraFov(0f)));
    }

    @ModifyExpressionValue(method = "getProjectionMatrixForCulling", at = @At(value = "INVOKE", target = "Ljava/lang/Math;max(FF)F"))
    
    private float golems$lobbyCullingFov(float original) {
        return LobbyScene.cullingFov(original);
    }
}
