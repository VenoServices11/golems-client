// Ported from OneClient's PolyPlus (https://github.com/Polyfrost/PolyPlus), GPL-3.0, by Polyfrost.
// Changed for Golem's Client.
package dev.golemsclient.oc.mixin.client.lobby;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import dev.golemsclient.oc.client.lobby.LobbyHologram;
import dev.golemsclient.oc.client.lobby.LobbyScene;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EntityRenderer.class)
public class MixinEntityRenderer {
    @ModifyExpressionValue(method = "extractShadow", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;"))
    private Object golems$forceLobbyShadows(Object original) {
        if (LobbyHologram.extracting) return Boolean.FALSE;
        return LobbyScene.terrainActive() ? Boolean.TRUE : original;
    }

    @ModifyExpressionValue(method = "extractShadow", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/entity/state/EntityRenderState;distanceToCameraSq:D"))
    private double golems$extendLobbyShadows(double original) {
        return LobbyScene.shadowDistance(original);
    }


    @ModifyReturnValue(method = "getPackedLightCoords", at = @At("RETURN"))
    private int golems$keyLightLobbyCast(int original, Entity entity, float partialTickTime) {
        return LobbyScene.lightFor(entity, original);
    }
}
