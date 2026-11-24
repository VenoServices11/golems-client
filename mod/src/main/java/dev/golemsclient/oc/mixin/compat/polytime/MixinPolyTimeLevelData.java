// Ported from OneClient's PolyPlus (https://github.com/Polyfrost/PolyPlus), GPL-3.0, by Polyfrost.
// Changed for Golem's Client.
package dev.golemsclient.oc.mixin.compat.polytime;

import net.minecraft.client.multiplayer.ClientLevel;
import dev.golemsclient.oc.client.lobby.LobbyScene;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientLevel.ClientLevelData.class)
public class MixinPolyTimeLevelData {
    @Shadow private long dayTime;

    @Inject(method = "getDayTime", at = @At("HEAD"), cancellable = true)
    private void golems$lobbyClock(CallbackInfoReturnable<Long> cir) {
        if (LobbyScene.terrainActive()) cir.setReturnValue(this.dayTime);
    }
}

