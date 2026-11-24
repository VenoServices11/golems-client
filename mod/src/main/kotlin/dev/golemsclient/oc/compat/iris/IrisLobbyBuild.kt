// Ported from OneClient's PolyPlus (https://github.com/Polyfrost/PolyPlus), GPL-3.0, by Polyfrost.
// Changed for Golem's Client.
package dev.golemsclient.oc.compat.iris

import java.util.function.BooleanSupplier

object IrisLobbyBuild {
    @Volatile
    private var building: BooleanSupplier = BooleanSupplier { false }

    @JvmStatic
    fun wire(source: BooleanSupplier) {
        building = source
    }

    @JvmStatic
    fun isBuilding(): Boolean = runCatching { building.asBoolean }.getOrDefault(false)
}
