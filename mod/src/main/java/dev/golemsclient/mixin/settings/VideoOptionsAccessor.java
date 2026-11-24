// Ported from OneClient's PolyPlus (https://github.com/Polyfrost/PolyPlus), GPL-3.0, by Polyfrost.
package dev.golemsclient.mixin.settings;

import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.options.VideoSettingsScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(VideoSettingsScreen.class)
public interface VideoOptionsAccessor {
    @Invoker("displayOptions")
    static OptionInstance<?>[] golemsclient$displayOptions(Options options) {
        throw new AssertionError();
    }

    @Invoker("qualityOptions")
    static OptionInstance<?>[] golemsclient$qualityOptions(Options options) {
        throw new AssertionError();
    }

    @Invoker("preferenceOptions")
    static OptionInstance<?>[] golemsclient$preferenceOptions(Options options) {
        throw new AssertionError();
    }
}
