package dev.golemsclient.mixin;

import dev.golemsclient.oc.LobbyBridge;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The lobby is quiet: only its own music and ambience (and menu clicks) play there. Minecraft's own sounds from the
 * scene (cave noises, animals, footsteps, water, weather...) are left out.
 */
@Mixin(SoundManager.class)
public abstract class LobbySoundsMixin {
    @Inject(method = "play", at = @At("HEAD"), cancellable = true)
    private void golemsclient$quietLobby(SoundInstance sound, CallbackInfoReturnable<SoundEngine.PlayResult> cir) {
        if (sound == null || !LobbyBridge.inLobby()) return;
        SoundSource source = sound.getSource();
        if (source == SoundSource.UI || source == SoundSource.MUSIC || source == SoundSource.MASTER) return;
        if ("golemsclient".equals(sound.getIdentifier().getNamespace())) return;
        cir.setReturnValue(SoundEngine.PlayResult.NOT_STARTED);
    }
}
