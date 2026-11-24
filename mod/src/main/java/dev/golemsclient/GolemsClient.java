package dev.golemsclient;

import dev.golemsclient.mixin.LoadingOverlayAccessor;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.gui.components.debug.DebugScreenEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Golem's Client in game: the window, the loading screen and F3 carry the Golem's Client name. */
public final class GolemsClient implements ClientModInitializer {
    public static final String MOD_ID = "golemsclient";
    public static final Logger LOGGER = LoggerFactory.getLogger("Golem's Client");

    /** Golem's Client's near-black, the launcher's page colour (Iron Dark). */
    public static final int BACKGROUND = 0xFF0E0E10;
    /** Poppy red, the launcher's accent. */
    public static final int ACCENT = 0xFFE5484D;

    @Override
    public void onInitializeClient() {
        // the loading screen's background: our near-black instead of Mojang's red (or black)
        LoadingOverlayAccessor.golemsclient$setBrandBackground(() -> BACKGROUND);

        // F3: "Golem's Client 1.0.0" above the Minecraft version line
        DebugScreenEntry version = DebugScreenEntries.getEntry(DebugScreenEntries.GAME_VERSION);
        if (version != null) DebugScreenEntries.register(DebugScreenEntries.GAME_VERSION, new BrandDebugEntry(version));

        dev.golemsclient.lobby.Lobby.register();
        DevShots.register();
        SceneMaker.register();
        LOGGER.info("Golem's Client {} (mod {})", version(), modVersion());
    }

    /** emeraldgolem3, the owner. */
    public static final java.util.UUID OWNER = java.util.UUID.fromString("dabe852b-94dd-4bb9-b18d-151c206be824");

    public static boolean isOwner(java.util.UUID id) {
        return OWNER.equals(id);
    }

    /**
     * The Golem's Client version people know: the launcher's (it passes -Dgolemsclient.version); the mod's own version
     * when the game was started some other way.
     */
    public static String version() {
        String launcher = System.getProperty("golemsclient.version");
        if (launcher != null && launcher.matches("[0-9A-Za-z.+-]{1,32}")) return launcher;
        String mod = modVersion();
        int plus = mod.indexOf('+');
        return plus > 0 ? mod.substring(0, plus) : mod;
    }

    public static String modVersion() {
        return FabricLoader.getInstance().getModContainer(MOD_ID)
                .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?");
    }
}
