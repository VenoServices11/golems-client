// Ported from OneClient's PolyPlus (https://github.com/Polyfrost/PolyPlus), GPL-3.0, by Polyfrost.
// Changed for Golem's Client: only the emote playback the lobby uses (the run-in, being dragged), no cosmetics.
package dev.golemsclient.oc.mixin.client.cosmetics;

import dev.golemsclient.oc.client.cosmetics.access.PlayerEmotesAccess;
import dev.golemsclient.oc.client.emotes.playback.EmoteController;
import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AbstractClientPlayer.class, priority = 1500)
public abstract class MixinAbstractClientPlayer implements PlayerEmotesAccess {
    @Unique
    private EmoteController golems$emoteController;

    @Override
    public EmoteController golems$emoteController() {
        EmoteController controller = golems$emoteController;
        if (controller == null) {
            controller = new EmoteController();
            golems$emoteController = controller;
        }
        return controller;
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void golems$tickEmote(CallbackInfo ci) {
        golems$emoteController().tick((AbstractClientPlayer) (Object) this);
    }
}
