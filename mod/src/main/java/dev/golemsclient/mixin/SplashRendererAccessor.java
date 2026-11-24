package dev.golemsclient.mixin;

import net.minecraft.client.gui.components.SplashRenderer;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The text of a splash Minecraft picked (from our splashes.txt, or a holiday one). */
@Mixin(SplashRenderer.class)
public interface SplashRendererAccessor {
    @Accessor("splash")
    Component golemsclient$text();
}
