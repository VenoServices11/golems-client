// Ported from OneClient's PolyPlus (https://github.com/Polyfrost/PolyPlus), GPL-3.0, by Polyfrost.
// Changed for Golem's Client.
package dev.golemsclient.oc.mixin.client.lobby;

import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import dev.golemsclient.oc.client.lobby.LobbyScene;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.level.Level;


@Mixin(value = Camera.class, priority = 2000)
public abstract class MixinLobbyCamera {
    @Shadow
    private boolean detached;

    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @Shadow
    protected abstract void setPosition(Vec3 position);

    @Inject(method = "setup", at = @At("TAIL"))
    private void golems$holdLobbyView(Level level, Entity entity, boolean detached, boolean mirrored, float partialTicks, CallbackInfo ci) {
        golems$hold(partialTicks);
    }
    

    @Unique
    private void golems$hold(float partialTicks) {
        Entity rig = LobbyScene.heldRig();
        if (rig == null) return;
        this.detached = false;
        this.setRotation(rig.getViewYRot(partialTicks), rig.getViewXRot(partialTicks));
        this.setPosition(rig.getEyePosition(partialTicks));
    }
}
