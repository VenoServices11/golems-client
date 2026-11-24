// Ported from OneClient's PolyPlus (https://github.com/Polyfrost/PolyPlus), GPL-3.0, by Polyfrost.
// Changed for Golem's Client.
//~ identifier
package dev.golemsclient.oc.mixin.compat.sodium;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

import java.util.regex.Pattern;
import net.caffeinemc.mods.sodium.client.gl.shader.ShaderLoader;
import net.minecraft.resources.Identifier;
import dev.golemsclient.oc.client.lobby.LobbySodiumWave;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;


@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.gl.shader.ShaderLoader", remap = false)
public abstract class MixinShaderLoaderLobby {
    @Unique
    private static final String golems$REQUEST = "golemsclient_lobby/";
    @Unique
    private static final String golems$NAMESPACE = "golemsclient";
    @Unique
    private static final String golems$LOBBY = "sodium_lobby/";
    @Unique
    private static final Pattern golems$CHUNK_FADE = Pattern.compile("\\bfadeFactor\\s*=\\s*([^;]*);");

    @ModifyVariable(method = "loadShader", at = @At("HEAD"), argsOnly = true, remap = false, require = 0, expect = 0)
    private static Identifier golems$lobbyShaderId(Identifier name) {
        if (!"sodium".equals(name.getNamespace()) || !name.getPath().startsWith(golems$REQUEST)) {
            return name;
        }
        return Identifier.fromNamespaceAndPath(golems$NAMESPACE, golems$LOBBY + name.getPath().substring(golems$REQUEST.length()));
    }

    @Inject(method = "getShaderSource", at = @At("HEAD"), cancellable = true, remap = false, require = 0, expect = 0)
    private static void golems$lobbyShaderSource(Identifier name, CallbackInfoReturnable<String> cir) {
        if (!golems$NAMESPACE.equals(name.getNamespace()) || !name.getPath().startsWith(golems$LOBBY)) {
            return;
        }
        String file = name.getPath().substring(golems$LOBBY.length());
        String source = ShaderLoader.getShaderSource(Identifier.fromNamespaceAndPath("sodium", "blocks/" + file));
        String patched;
        if (file.endsWith(".vsh")) {
            patched = LobbySodiumWave.patchVertex(source, LobbySodiumWave.Flavour.GL);
            if (patched != null) {
                patched = golems$CHUNK_FADE.matcher(patched).replaceAll("fadeFactor = u_PolyLobbyWave.x >= 0.0 ? 1.0 : ($1);");
            }
        } else {
            patched = LobbySodiumWave.patchFragment(source, source.contains(LobbySodiumWave.Flavour.GL_DIFFUSE.getFragmentAnchor()) ? LobbySodiumWave.Flavour.GL_DIFFUSE : LobbySodiumWave.Flavour.GL);
        }
        cir.setReturnValue(patched != null ? patched : source);
    }
    
}
