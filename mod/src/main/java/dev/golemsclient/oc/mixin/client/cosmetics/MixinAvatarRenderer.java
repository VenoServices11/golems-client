// Ported from OneClient's PolyPlus (https://github.com/Polyfrost/PolyPlus), GPL-3.0, by Polyfrost.
// Changed for Golem's Client: only the emote playback the lobby uses (the run-in, being dragged), no cosmetics.
package dev.golemsclient.oc.mixin.client.cosmetics;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.golemsclient.oc.client.cosmetics.access.AvatarEmoteRenderAccess;
import dev.golemsclient.oc.client.cosmetics.access.PlayerEmotesAccess;
import dev.golemsclient.oc.client.emotes.effects.EmoteEffectRenderLayer;
import dev.golemsclient.oc.client.emotes.playback.EmoteController;
import dev.golemsclient.oc.client.lobby.LobbyAntics;
import dev.golemsclient.oc.client.lobby.LobbyHerobrine;
import dev.golemsclient.oc.client.lobby.LobbyHerobrineLayer;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
public class MixinAvatarRenderer {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void golems$addLayers(EntityRendererProvider.Context context, boolean slimSteve, CallbackInfo ci) {
        LivingEntityRendererInvoker invoker = (LivingEntityRendererInvoker) this;
        @SuppressWarnings("unchecked")
        RenderLayerParent<AvatarRenderState, PlayerModel> parent = (RenderLayerParent<AvatarRenderState, PlayerModel>) this;
        invoker.golems$invokeAddLayer(new EmoteEffectRenderLayer(parent));
        invoker.golems$invokeAddLayer(new LobbyHerobrineLayer(parent));
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V", at = @At("RETURN"))
    private void golems$bindEmoteState(Avatar entity, AvatarRenderState state, float partialTicks, CallbackInfo ci) {
        if (!(state instanceof AvatarEmoteRenderAccess renderAccess)) return;
        EmoteController controller = entity instanceof PlayerEmotesAccess playerAccess ? playerAccess.golems$emoteController() : null;
        if (controller != null) renderAccess.golems$bindEmoteController(controller);
    }

    @Inject(method = "setupRotations(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;FF)V", at = @At("HEAD"))
    private void golems$leanHerobrine(AvatarRenderState state, PoseStack poseStack, float bodyRot, float scale, CallbackInfo ci) {
        LobbyHerobrine.lean(state.id, poseStack);
        LobbyAntics.spin(state.id, poseStack);
    }
}
