// Ported from OneClient's PolyPlus (https://github.com/Polyfrost/PolyPlus), GPL-3.0, by Polyfrost.
// Changed for Golem's Client.
package dev.golemsclient.oc.mixin.client.lobby;

import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.attribute.EnvironmentAttributeSystem;
import net.minecraft.world.attribute.SpatialAttributeInterpolator;
import net.minecraft.world.phys.Vec3;
import dev.golemsclient.oc.client.lobby.LobbyScene;
import dev.golemsclient.oc.client.lobby.LobbySky;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EnvironmentAttributeSystem.class)
public class MixinEnvironmentAttributeSystem {
    @Inject(
        method = "getValue(Lnet/minecraft/world/attribute/EnvironmentAttribute;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/attribute/SpatialAttributeInterpolator;)Ljava/lang/Object;",
        at = @At("RETURN"),
        cancellable = true
    )
    private void golems$livingSky(EnvironmentAttribute<?> attribute, Vec3 pos, SpatialAttributeInterpolator interpolator, CallbackInfoReturnable<Object> cir) {
        if (LobbyScene.terrainActive()) cir.setReturnValue(LobbySky.adjust(attribute, cir.getReturnValue()));
    }
}
