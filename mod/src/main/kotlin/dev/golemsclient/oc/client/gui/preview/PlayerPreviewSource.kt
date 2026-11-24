// Ported from OneClient's PolyPlus (https://github.com/Polyfrost/PolyPlus), GPL-3.0, by Polyfrost.
// Changed for Golem's Client: only the parts the lobby's wardrobe framing reads.
package dev.golemsclient.oc.client.gui.preview

import net.minecraft.resources.Identifier

data class PreviewSkin(val texture: Identifier, val slim: Boolean)

sealed interface PlayerPreviewSource {
    data class Override(
        val skin: PreviewSkin? = null,
        val capeCosmeticId: Int? = null,
        val mojangCape: Identifier? = null,
    ) : PlayerPreviewSource
}
