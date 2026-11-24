package dev.golemsclient.menu;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.PlayerSkin;

import java.util.function.Supplier;

/**
 * Your skin as the menus and the lobby show it. It starts as the one the game launched with; after you change your
 * skin in the Wardrobe it's the new one, without restarting the game.
 */
public final class AccountSkin {
    private static GameProfile profile;
    private static Supplier<PlayerSkin> lookup;

    private AccountSkin() {
    }

    public static Supplier<PlayerSkin> lookup() {
        Minecraft mc = Minecraft.getInstance();
        if (lookup == null) {
            profile = mc.getGameProfile();
            lookup = mc.getSkinManager().createLookup(profile, false);
        }
        return lookup;
    }

    /** The new profile from Mojang's session server, with the new skin. */
    static void update(GameProfile fresh) {
        Minecraft mc = Minecraft.getInstance();
        profile = fresh;
        lookup = mc.getSkinManager().createLookup(fresh, false);
        // the lobby's player too
        dev.golemsclient.oc.client.cosmetics.LocalAccountSkin.INSTANCE.onAccountSwitched(fresh.id(), fresh);
    }
}
