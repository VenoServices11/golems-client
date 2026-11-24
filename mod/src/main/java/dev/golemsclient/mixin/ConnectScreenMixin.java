package dev.golemsclient.mixin;

import dev.golemsclient.menu.MenuData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.TransferState;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Remembers each server joined (from anywhere), so the main menu can offer it again. */
@Mixin(ConnectScreen.class)
public abstract class ConnectScreenMixin {
    @Inject(method = "startConnecting", at = @At("HEAD"))
    private static void golemsclient$remember(Screen parent, Minecraft minecraft, ServerAddress address, ServerData data,
                                              boolean quickPlay, TransferState transfer, CallbackInfo ci) {
        // transfers move you between servers of one network: keep the address you chose
        if (transfer == null && data != null && !(minecraft.screen instanceof ConnectScreen)) {
            MenuData.played(data.name, data.ip);
        }
    }
}
