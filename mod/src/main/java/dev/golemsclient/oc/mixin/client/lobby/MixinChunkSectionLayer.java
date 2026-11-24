// Ported from OneClient's PolyPlus (https://github.com/Polyfrost/PolyPlus), GPL-3.0, by Polyfrost.
// Changed for Golem's Client.
package dev.golemsclient.oc.mixin.client.lobby;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.blaze3d.pipeline.RenderPipeline;

import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import dev.golemsclient.oc.client.lobby.LobbyPipelines;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ChunkSectionLayer.class)
public class MixinChunkSectionLayer {
    @ModifyReturnValue(method = "pipeline", at = @At("RETURN"))
    private RenderPipeline golems$lobbyTerrain(RenderPipeline original) {
        return LobbyPipelines.select((ChunkSectionLayer) (Object) this, false, original);
    }
    
}
