// Ported from OneClient's PolyPlus (https://github.com/Polyfrost/PolyPlus), GPL-3.0, by Polyfrost.
// Changed for Golem's Client.
package dev.golemsclient.oc.mixin.client.cosmetics;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;
import java.util.List;

@Mixin(LivingEntityRenderer.class)
public interface LivingEntityRendererInvoker {
    @SuppressWarnings("UnusedReturnValue")
    @Invoker("addLayer")
    boolean golems$invokeAddLayer(RenderLayer<?, ?> layer);

    @Accessor("layers")
    List<RenderLayer<?, ?>> golems$layers();
}
