// Ported from OneClient's PolyPlus (https://github.com/Polyfrost/PolyPlus), GPL-3.0, by Polyfrost.
package dev.golemsclient.mixin.settings;

import net.minecraft.client.OptionInstance;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.function.Function;

@Mixin(OptionInstance.class)
public interface OptionInstanceAccessor {
    @Accessor("toString")
    Function<Object, Component> golemsclient$valueLabel();

    @Accessor("tooltip")
    OptionInstance.TooltipSupplier<Object> golemsclient$tooltip();

    @Accessor("caption")
    Component golemsclient$caption();
}
