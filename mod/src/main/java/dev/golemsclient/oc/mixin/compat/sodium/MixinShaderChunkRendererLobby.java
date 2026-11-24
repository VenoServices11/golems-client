// Ported from OneClient's PolyPlus (https://github.com/Polyfrost/PolyPlus), GPL-3.0, by Polyfrost.
// Changed for Golem's Client.
package dev.golemsclient.oc.mixin.compat.sodium;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import net.caffeinemc.mods.sodium.client.gl.shader.GlProgram;
import net.caffeinemc.mods.sodium.client.gl.shader.uniform.GlUniformFloat2v;
import net.caffeinemc.mods.sodium.client.render.chunk.ShaderChunkRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.shader.ChunkShaderInterface;
import net.caffeinemc.mods.sodium.client.render.chunk.shader.ChunkShaderOptions;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import dev.golemsclient.oc.client.lobby.LobbySodiumWave;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;


@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.ShaderChunkRenderer", remap = false)
public abstract class MixinShaderChunkRendererLobby {
    @Unique
    private static final Logger golems$LOGGER = LogManager.getLogger("Golem's Client/Lobby");
    @Unique
    private static final String golems$LOBBY_PATH = "golemsclient_lobby/block_layer_opaque";
    @Unique
    private static final String golems$WAVE_UNIFORM = "u_PolyLobbyWave";

    @Unique
    private static boolean golems$resolved;
    @Unique
    private static MethodHandle golems$createShader;
    @Unique
    private static MethodHandle golems$activeProgram;

    @Unique
    private final Map<ChunkShaderOptions, GlProgram<ChunkShaderInterface>> golems$lobbyPrograms = new HashMap<>();
    @Unique
    private final Map<GlProgram<ChunkShaderInterface>, GlUniformFloat2v> golems$waveUniforms = new IdentityHashMap<>();

    @Unique
    private static boolean golems$resolve() {
        if (!golems$resolved) {
            golems$resolved = true;
            try {
                Method create = ShaderChunkRenderer.class.getDeclaredMethod("createShader", String.class, ChunkShaderOptions.class);
                create.setAccessible(true);
                Field active = ShaderChunkRenderer.class.getDeclaredField("activeProgram");
                active.setAccessible(true);
                MethodHandles.Lookup lookup = MethodHandles.lookup();
                golems$activeProgram = lookup.unreflectGetter(active);
                golems$createShader = lookup.unreflect(create);
            } catch (Throwable error) {
                golems$createShader = null;
                golems$activeProgram = null;
                LobbySodiumWave.fail("Sodium's ShaderChunkRenderer has no createShader(String, ChunkShaderOptions) or activeProgram", error);
            }
        }
        return golems$createShader != null;
    }

    @Inject(method = "compileProgram(Lnet/caffeinemc/mods/sodium/client/render/chunk/shader/ChunkShaderOptions;)Lnet/caffeinemc/mods/sodium/client/gl/shader/GlProgram;", at = @At("HEAD"), cancellable = true, remap = false, require = 0, expect = 0)
    private void golems$lobbyProgram(ChunkShaderOptions options, CallbackInfoReturnable<GlProgram<ChunkShaderInterface>> cir) {
        if (!LobbySodiumWave.active()) {
            return;
        }
        GlProgram<ChunkShaderInterface> program = this.golems$lobbyPrograms.get(options);
        if (program == null) {
            program = this.golems$buildLobbyProgram(options);
        }
        if (program != null) {
            cir.setReturnValue(program);
        }
    }

    @Unique
    @SuppressWarnings("unchecked")
    private GlProgram<ChunkShaderInterface> golems$buildLobbyProgram(ChunkShaderOptions options) {
        if (!golems$resolve()) {
            return null;
        }
        GlProgram<ChunkShaderInterface> program;
        GlUniformFloat2v wave;
        try {
            Object built = golems$createShader.invoke((Object) this, golems$LOBBY_PATH, options);
            program = (GlProgram<ChunkShaderInterface>) built;
            wave = program.bindUniformOptional(golems$WAVE_UNIFORM, GlUniformFloat2v::new);
        } catch (Throwable error) {
            LobbySodiumWave.fail("building the lobby chunk program", error);
            return null;
        }
        if (wave == null || !LobbySodiumWave.active()) {
            program.delete();
            if (wave == null) {
                LobbySodiumWave.fail("the lobby chunk program has no " + golems$WAVE_UNIFORM, null);
            }
            return null;
        }
        this.golems$lobbyPrograms.put(options, program);
        this.golems$waveUniforms.put(program, wave);
        golems$LOGGER.info("Built Sodium lobby chunk program for pass {}", options.pass());
        return program;
    }

    @Inject(method = "begin", at = @At("TAIL"), remap = false, require = 0, expect = 0)
    private void golems$lobbyWave(CallbackInfo ci) {
        if (this.golems$waveUniforms.isEmpty() || golems$activeProgram == null) {
            return;
        }
        Object active;
        try {
            active = golems$activeProgram.invoke((Object) this);
        } catch (Throwable error) {
            return;
        }
        GlUniformFloat2v wave = this.golems$waveUniforms.get(active);
        if (wave != null) {
            wave.set(LobbySodiumWave.seconds(), LobbySodiumWave.aim());
        }
    }

    @Inject(method = "delete", at = @At("TAIL"), remap = false, require = 0, expect = 0)
    private void golems$deleteLobbyPrograms(CallbackInfo ci) {
        this.golems$lobbyPrograms.values().forEach(GlProgram::delete);
        this.golems$lobbyPrograms.clear();
        this.golems$waveUniforms.clear();
    }
    
}
