package dev.golemsclient.mixin;

import dev.golemsclient.GolemsClient;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The window title: "Golem's Client 1.0.0 - Minecraft 1.21.11", keeping Minecraft's " - Singleplayer" and the like. */
@Mixin(Minecraft.class)
public abstract class MinecraftTitleMixin {
    @Inject(method = "createTitle", at = @At("RETURN"), cancellable = true)
    private void golemsclient$title(CallbackInfoReturnable<String> cir) {
        String vanilla = cir.getReturnValue();
        String where = "";
        if (vanilla != null) {
            int dash = vanilla.indexOf(" - ");
            if (dash >= 0) where = vanilla.substring(dash);
        }
        cir.setReturnValue("Golem's Client " + GolemsClient.version() + " - Minecraft "
                + SharedConstants.getCurrentVersion().name() + where);
    }
}
