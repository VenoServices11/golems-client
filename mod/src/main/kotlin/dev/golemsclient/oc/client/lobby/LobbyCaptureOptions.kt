// Ported from OneClient's PolyPlus (https://github.com/Polyfrost/PolyPlus), GPL-3.0, by Polyfrost.
// Changed for Golem's Client.
package dev.golemsclient.oc.client.lobby

enum class LobbyCaptureFrequency(private val label: String, val scale: Double, val restTicks: Int) {
    OFTEN("Often", 0.5, 0),
    NORMAL("Normal", 1.0, 0),
    RARELY("Rarely", 3.0, 18_000),
    OFF("Off", 1.0, 0);

    fun ticks(base: Int): Int = (base * scale).toInt().coerceAtLeast(1)

    override fun toString(): String = label
}

enum class LobbyCaptureView(private val label: String) {
    BEST("Best"),
    RANDOM("Random");

    override fun toString(): String = label
}
