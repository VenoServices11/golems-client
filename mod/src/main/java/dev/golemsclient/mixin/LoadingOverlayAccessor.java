package dev.golemsclient.mixin;

import net.minecraft.client.gui.screens.LoadingOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.function.IntSupplier;

/** Swaps the loading screen's background colour for Golem's Client's. */
@Mixin(LoadingOverlay.class)
public interface LoadingOverlayAccessor {
    @Accessor("BRAND_BACKGROUND")
    @Mutable
    static void golemsclient$setBrandBackground(IntSupplier colour) {
        throw new AssertionError();
    }
}
