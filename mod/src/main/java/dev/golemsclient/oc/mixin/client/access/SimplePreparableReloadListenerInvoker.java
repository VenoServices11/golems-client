// Ported from OneClient's PolyPlus (https://github.com/Polyfrost/PolyPlus), GPL-3.0, by Polyfrost.
// Changed for Golem's Client.
package dev.golemsclient.oc.mixin.client.access;

import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(SimplePreparableReloadListener.class)
public interface SimplePreparableReloadListenerInvoker {
    @Invoker("prepare")
    Object golems$prepare(ResourceManager manager, ProfilerFiller profiler);

    @Invoker("apply")
    void golems$apply(Object prepared, ResourceManager manager, ProfilerFiller profiler);
}
