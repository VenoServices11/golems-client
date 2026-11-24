package dev.golemsclient.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import dev.golemsclient.render.LoadingScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The loading screen in Golem's Client's look: Mojang's logo and white bar are skipped, and {@link LoadingScreen}
 * draws the G, the name, a red progress bar and a tip in their place (on top of the frame, with smooth text).
 */
@Mixin(LoadingOverlay.class)
public abstract class LoadingOverlayMixin {
    @Shadow
    private float currentProgress;

    /** Mojang's logo is drawn in two halves; neither is drawn, but where and how visible it is goes to our screen. */
    @Redirect(method = "render", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;blit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIIIIII)V"))
    private void golemsclient$logo(GuiGraphics g, RenderPipeline pipeline, Identifier texture, int x, int y, float u, float v,
                                   int width, int height, int regionW, int regionH, int textureW, int textureH, int color) {
        LoadingScreen.logoHalf(x, y, width, height, ((color >>> 24) & 0xFF) / 255f);
    }

    @Inject(method = "drawProgressBar", at = @At("HEAD"), cancellable = true)
    private void golemsclient$progressBar(GuiGraphics g, int x0, int y0, int x1, int y1, float opacity, CallbackInfo ci) {
        LoadingScreen.progress(x0, y0, x1, y1, currentProgress, opacity);
        ci.cancel();
    }
}
