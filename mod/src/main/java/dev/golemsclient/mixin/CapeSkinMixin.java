package dev.golemsclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.golemsclient.friends.Capes;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Players wear their Golem's Client cape (in place of a Mojang cape, when they picked one). */
@Mixin(AbstractClientPlayer.class)
public abstract class CapeSkinMixin {
    @ModifyReturnValue(method = "getSkin", at = @At("RETURN"))
    private PlayerSkin golemsclient$cape(PlayerSkin original) {
        return Capes.apply(((AbstractClientPlayer) (Object) this).getUUID(), original);
    }
}
