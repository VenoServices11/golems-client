// Ported from OneClient's PolyPlus (https://github.com/Polyfrost/PolyPlus), GPL-3.0, by Polyfrost.
// Changed for Golem's Client.
package dev.golemsclient.oc.mixin.client.lobby;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.server.packs.resources.ReloadInstance;
import dev.golemsclient.oc.client.lobby.LobbyScene;
import dev.golemsclient.oc.client.lobby.StartupOverlay;
import dev.golemsclient.oc.client.utils.ClientPlatform;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gui.GuiGraphics;


import java.util.Optional;
import java.util.function.Consumer;

@Mixin(value = LoadingOverlay.class, priority = 1500)
public class MixinLoadingOverlay {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    @Final
    private ReloadInstance reload;

    @Shadow
    @Final
    private boolean fadeIn;

    @Shadow
    private long fadeOutStart;

    @Shadow
    @Final
    private Consumer<Optional<Throwable>> onFinish;

    @Unique
    private boolean golems$branded;

    @Unique
    private StartupOverlay.ReloadState golems$reloadState;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void golems$claimInitialLoad(CallbackInfo ci) {
        LobbyScene.noteReload(reload::isDone);
        golems$branded = StartupOverlay.begin(fadeIn, minecraft.isGameLoadFinished());
        if (!golems$branded && StartupOverlay.ownsReload(fadeIn)) golems$reloadState = new StartupOverlay.ReloadState();
    }

    @WrapMethod(method = "render")
    private void golems$drawReload(GuiGraphics graphics, int mouseX, int mouseY, float partial, Operation<Void> original) {
    
        if (golems$reloadState == null) {
            original.call(graphics, mouseX, mouseY, partial);
            return;
        }
        golems$finishReload(graphics.guiWidth(), graphics.guiHeight());
        if (StartupOverlay.reloadFrame(minecraft, graphics, partial, golems$reloadState, reload.getActualProgress(), fadeOutStart > -1L)) {
            golems$close();
        }
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void golems$drawStartup(GuiGraphics graphics, int mouseX, int mouseY, float partial, CallbackInfo ci) {
    
        if (!golems$branded) return;
        boolean golems$visible = graphics.getClass() == GuiGraphics.class;
        
        if (!golems$visible) {
            golems$branded = false;
            StartupOverlay.abandon(reload.done());
            return;
        }
        ci.cancel();
        StartupOverlay.frame(minecraft, graphics, partial, reload.getActualProgress(), fadeOutStart > -1L);
    }

    @Unique
    private void golems$finishReload(int width, int height) {
        if (fadeOutStart != -1L || !reload.isDone()) return;
        try {
            reload.checkExceptions();
            onFinish.accept(Optional.empty());
        } catch (Throwable error) {
            onFinish.accept(Optional.of(error));
        }
        fadeOutStart = System.currentTimeMillis();
        Screen screen = ClientPlatform.INSTANCE.currentScreen();
        if (screen == null) return;
        screen.init(width, height);
    }

    @Unique
    private void golems$close() {
        if (minecraft.getOverlay() == (Object) this) minecraft.setOverlay(null);
        
    }
}
