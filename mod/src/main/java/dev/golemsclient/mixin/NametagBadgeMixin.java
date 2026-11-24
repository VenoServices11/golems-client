package dev.golemsclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.golemsclient.badge.Badges;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The G badge in front of a Golem's Client player's nametag (as OneClient does it, see PolyPlus, GPL-3.0). */
@Mixin(EntityRenderer.class)
public class NametagBadgeMixin {
    @ModifyReturnValue(method = "getNameTag", at = @At("RETURN"))
    private Component golemsclient$badge(Component original, Entity entity) {
        if (original != null && entity instanceof AbstractClientPlayer player) return Badges.decorate(original, player.getUUID());
        return original;
    }
}
