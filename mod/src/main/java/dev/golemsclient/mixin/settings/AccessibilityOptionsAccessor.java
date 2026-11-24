// Ported from OneClient's PolyPlus (https://github.com/Polyfrost/PolyPlus), GPL-3.0, by Polyfrost.
package dev.golemsclient.mixin.settings;

import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.options.AccessibilityOptionsScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(AccessibilityOptionsScreen.class)
public interface AccessibilityOptionsAccessor {
    @Invoker("options")
    static OptionInstance<?>[] golemsclient$options(Options options) {
        throw new AssertionError();
    }
}
