// Ported from OneClient's PolyPlus (https://github.com/Polyfrost/PolyPlus), GPL-3.0, by Polyfrost.
// Changed for Golem's Client.
package dev.golemsclient.oc.mixin.client.access;

import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Camera.class)
public interface CameraAccessor {
    @Accessor("position")
    void golems$setPosition(Vec3 position);

    @Accessor("eyeHeight")
    void golems$setEyeHeight(float eyeHeight);

    @Accessor("eyeHeightOld")
    void golems$setEyeHeightOld(float eyeHeightOld);
}
