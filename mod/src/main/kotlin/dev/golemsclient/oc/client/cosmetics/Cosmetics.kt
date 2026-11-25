// Golem's Client: OneClient's cosmetics come from Polyfrost's servers, which Golem's Client doesn't use. These keep the
// lobby's cosmetic hooks in place, empty, until Golem's Client has its own.
package dev.golemsclient.oc.client.cosmetics

import com.mojang.authlib.GameProfile
import dev.golemsclient.oc.client.gui.preview.PreviewSkin
import net.minecraft.core.ClientAsset
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.player.PlayerSkin
import net.minecraft.world.entity.player.PlayerModelType
import java.util.UUID

enum class BodySlot(val serializedName: String, val displayName: String) {
    Hat("hat", "Head"),
    Glasses("glasses", "Face"),
    Backpack("backpack", "Back"),
    Cape("cape", "Cape"),
    Wings("wings", "Wings"),
    Shoulder("shoulder", "Shoulder"),
    LeftHand("left_hand", "Left Hand"),
    RightHand("right_hand", "Right Hand"),
    Boots("boots", "Feet"),
    Aura("aura", "Aura"),
    Pet("pet", "Pet"),
    Unknown("unknown", "Unknown");
}

object CapeResolver {
    // Golem's Client: the lobby's players wear their Golem's Client capes too
    fun applyCape(playerId: UUID, profile: GameProfile, original: PlayerSkin): PlayerSkin =
        dev.golemsclient.friends.Capes.apply(playerId, original)
}

object LocalSkinOverride {
    fun apply(playerId: UUID, original: PlayerSkin): PlayerSkin = original

    fun withSkin(original: PlayerSkin, skin: PreviewSkin): PlayerSkin = PlayerSkin(
        ClientAsset.ResourceTexture(skin.texture),
        original.cape(),
        original.elytra(),
        if (skin.slim) PlayerModelType.SLIM else PlayerModelType.WIDE,
        original.secure(),
    )
}

object CosmeticAssetCache {
    fun getCapeResource(id: Int): Identifier? = null
}
