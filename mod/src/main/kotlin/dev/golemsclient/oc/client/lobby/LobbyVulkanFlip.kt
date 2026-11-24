// Ported from OneClient's PolyPlus (https://github.com/Polyfrost/PolyPlus), GPL-3.0, by Polyfrost.
// Changed for Golem's Client.
package dev.golemsclient.oc.client.lobby

import dev.golemsclient.oc.compat.Mods

object LobbyVulkanFlip {
    private const val NAMESPACE = "golemsclient"
    private const val PREFIX = "post/lobby"
    private val HEADER = Regex("^[ \t]*#(version|extension)\\b.*$", RegexOption.MULTILINE)
    private const val FLIP = "\n#define texture(s, uv) texture(s, vec2((uv).x, 1.0 - (uv).y))\n#define textureLod(s, uv, lod) textureLod(s, vec2((uv).x, 1.0 - (uv).y), lod)\n"

    @JvmStatic
    fun patch(namespace: String, path: String, fragment: Boolean, source: String?): String? {
        if (source == null || !fragment || !Mods.vulkanMod || namespace != NAMESPACE || !path.startsWith(PREFIX)) return source
        val end = HEADER.findAll(source).lastOrNull()?.range?.last ?: return source
        return source.substring(0, end + 1) + FLIP + source.substring(end + 1)
    }
}
