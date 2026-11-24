// Ported from OneClient's PolyPlus (https://github.com/Polyfrost/PolyPlus), GPL-3.0, by Polyfrost.
package dev.golemsclient.mixin.settings;

import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.options.controls.ControlsScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ControlsScreen.class)
public interface ControlsOptionsAccessor {
    @Invoker("options")
    static OptionInstance<?>[] golemsclient$options(Options options) {
        throw new AssertionError();
    }
}
