// Ported from OneClient's PolyPlus (https://github.com/Polyfrost/PolyPlus), GPL-3.0, by Polyfrost.
// Changed for Golem's Client: only the emote playback the lobby uses (the run-in, being dragged), no cosmetics.
package dev.golemsclient.oc.mixin.client.cosmetics;

import dev.golemsclient.oc.client.cosmetics.access.AvatarEmoteRenderAccess;
import dev.golemsclient.oc.client.emotes.playback.EmoteController;
import dev.golemsclient.oc.client.lobby.LobbyHerobrine;
import dev.golemsclient.oc.client.render.PlayerRenderContext;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerModel.class)
public class MixinPlayerModel {
    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("RETURN"))
    private void golems$tiltHerobrine(AvatarRenderState state, CallbackInfo ci) {
        ((PlayerModel) (Object) this).head.zRot += LobbyHerobrine.tilt(state.id);
    }

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("RETURN"))
    private void golems$applyEmote(AvatarRenderState state, CallbackInfo ci) {
        if (!(state instanceof AvatarEmoteRenderAccess renderAccess)) return;
        EmoteController controller = renderAccess.golems$boundEmoteController();
        if (!controller.isActive()) return;
        PlayerModel model = (PlayerModel) (Object) this;
        renderAccess.golems$setLastEmoteSample(controller.applyToModel(model, PlayerRenderContext.Companion.from(state)));
    }
}
