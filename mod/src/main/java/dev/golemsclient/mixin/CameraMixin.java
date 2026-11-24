package dev.golemsclient.mixin;

import dev.golemsclient.lobby.Lobby;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** In the lobby the camera stands where the scene says (and swoops in at the start), not behind your player. */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    protected abstract void setPosition(double x, double y, double z);

    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Inject(method = "setup", at = @At("TAIL"))
    private void golemsclient$lobbyCamera(Level level, Entity entity, boolean detached, boolean mirrored, float partialTick, CallbackInfo ci) {
        if (!Lobby.ownsCamera()) return;
        double[] c = Lobby.camera();
        setRotation((float) c[3], (float) c[4]);
        setPosition(c[0], c[1], c[2]);
    }
}
