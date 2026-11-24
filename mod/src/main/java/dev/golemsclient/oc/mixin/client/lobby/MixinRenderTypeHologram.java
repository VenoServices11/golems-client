// Ported from OneClient's PolyPlus (https://github.com/Polyfrost/PolyPlus), GPL-3.0, by Polyfrost.
// Changed for Golem's Client.
package dev.golemsclient.oc.mixin.client.lobby;

import com.mojang.blaze3d.pipeline.RenderPipeline;

import net.minecraft.client.renderer.rendertype.RenderType;

import dev.golemsclient.oc.client.lobby.LobbyPipelines;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

// outranks VulkanMod's default-priority @Overwrite of draw, which otherwise rejects INVOKE injectors
@Mixin(value = RenderType.class, priority = 1100)

public class MixinRenderTypeHologram {
    @ModifyArg(method = "draw", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderPass;setPipeline(Lcom/mojang/blaze3d/pipeline/RenderPipeline;)V"))
    
    private RenderPipeline golems$hologram(RenderPipeline original) {
        return LobbyPipelines.hologram(original);
    }
}
