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

        // OneClient's lobby: its startup chime is loaded early so it can play the moment the game is ready
        dev.golemsclient.oc.client.lobby.StartupSounds.INSTANCE.prepare();
        // the first-launch setup: the startup logo flies into its card when it's waiting
        dev.golemsclient.oc.LobbyBridge.setupPending(!dev.golemsclient.menu.MenuData.get().setupDone);
        // play time per world and server, for the Stats page
        dev.golemsclient.stats.PlaySessions.register();
        // the G badge on Golem's Client players' nametags and in the tab list
        dev.golemsclient.badge.Badges.register();
        dev.golemsclient.friends.Friends.register();
        dev.golemsclient.friends.Friends.onNotice(message -> net.minecraft.client.Minecraft.getInstance().execute(() -> {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.screen instanceof dev.golemsclient.menu.MenuScreen menu) menu.notice(message);
            else net.minecraft.client.gui.components.toasts.SystemToast.add(mc.getToastManager(),
                    net.minecraft.client.gui.components.toasts.SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                    net.minecraft.network.chat.Component.literal("Golem's Client"), net.minecraft.network.chat.Component.literal(message));
        }));
        // where you are, for the launcher's Discord status
        DiscordStatus.register();
        // OneConfig's menu in Golem's Client's colours
        dev.golemsclient.oneconfig.OneConfigBrand.register();
        DevShots.register();
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
