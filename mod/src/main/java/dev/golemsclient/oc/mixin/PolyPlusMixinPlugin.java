// Ported from OneClient's PolyPlus (https://github.com/Polyfrost/PolyPlus), GPL-3.0, by Polyfrost.
// Changed for Golem's Client.
package dev.golemsclient.oc.mixin;

import com.moulberry.mixinconstraints.ConstraintsMixinPlugin;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class PolyPlusMixinPlugin extends ConstraintsMixinPlugin {
    private static final Logger LOGGER = LogManager.getLogger("Golem's Client/Compat");
    private static final String COMPAT = "dev.golemsclient.oc.mixin.compat.";
    private static final String SODIUM = "sodium";
    private static final String IRIS = "iris";
    private static final String ARGENTUM = "argentum";
    private static final String CELERITAS = "org_embeddedt_celeritas_celeritas-common";

    private static final Map<String, String> TESTED = new HashMap<>();
    private static final Map<String, String[]> GATED = new HashMap<>();
    private static final Map<String, Boolean> CHECKED = new ConcurrentHashMap<>();

    static {
        TESTED.put(SODIUM, "0.8");
        TESTED.put(IRIS, "1.10");
        

        gate("sodium.MixinDefaultChunkRendererLobby", SODIUM);
        gate("sodium.MixinShaderChunkRendererLobby", SODIUM);
        gate("sodium.MixinShaderChunkRendererPipelineLobby", SODIUM);
        gate("sodium.MixinShaderLoaderLobby", SODIUM);
        gate("sodium.MixinSodiumWorldRendererLobby", SODIUM);
        gate("iris.MixinIrisDHCompat", IRIS);
        gate("iris.MixinIrisPipelineManager", IRIS);
        gate("iris.MixinIrisTransformPatcher", IRIS);
        gate("argentum.MixinCeleritasBlockEntityLobby", ARGENTUM, CELERITAS);
        gate("argentum.MixinCeleritasShaderChunkRendererLobby", ARGENTUM, CELERITAS);
        gate("argentum.MixinCeleritasShaderLoaderLobby", ARGENTUM, CELERITAS);
    }

    private static void gate(String mixin, String... mods) {
        GATED.put(COMPAT + mixin, mods);
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        String[] mods = GATED.get(mixinClassName);
        if (mods != null) {
            for (String mod : mods) {
                if (!compatEnabled(mod)) return false;
            }
        }
        return super.shouldApplyMixin(targetClassName, mixinClassName);
    }

    public static boolean compatEnabled(String modId) {
        return CHECKED.computeIfAbsent(modId, PolyPlusMixinPlugin::check);
    }

    private static boolean check(String modId) {
        String tested = TESTED.get(modId);
        if (tested == null) return true;
        Optional<ModContainer> mod = FabricLoader.getInstance().getModContainer(modId);
        if (!mod.isPresent()) return true;
        String version = mod.get().getMetadata().getVersion().getFriendlyString();
        if (version.startsWith(tested + ".")) return true;
        LOGGER.warn("Lobby enhancements for {} are disabled: version {} is outside the tested {}.x range", modId, version, tested);
        return false;
    }
}
