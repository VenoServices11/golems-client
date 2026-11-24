// Ported from OneClient's PolyPlus (https://github.com/Polyfrost/PolyPlus), GPL-3.0, by Polyfrost.
// Changed for Golem's Client.
package dev.golemsclient.oc.mixin.client.lobby;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.resources.Identifier;
import dev.golemsclient.oc.client.lobby.LobbySky;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.blaze3d.shaders.ShaderType;
import dev.golemsclient.oc.client.lobby.LobbyVulkanFlip;


@Mixin(ShaderManager.class)
public class MixinShaderManagerLobby {
    @WrapWithCondition(method = "getPostChain", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ShaderManager;tryTriggerRecovery(Ljava/lang/Exception;)V"))
    private boolean golems$keepPacksOnLobbyFailure(ShaderManager manager, Exception exception, @Local(argsOnly = true) Identifier id) {
        return !LobbySky.ownsPostEffect(id);
    }

    @ModifyReturnValue(method = "getShader", at = @At("RETURN"))
    private String golems$flipLobbyForVulkan(String original, Identifier id, ShaderType type) {
        return LobbyVulkanFlip.patch(id.getNamespace(), id.getPath(), type == ShaderType.FRAGMENT, original);
    }
    

}
