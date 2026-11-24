// Ported from OneClient's PolyPlus (https://github.com/Polyfrost/PolyPlus), GPL-3.0, by Polyfrost.
// Changed for Golem's Client.
package dev.golemsclient.oc.client.lobby

import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag

fun CompoundTag.optCompound(name: String): CompoundTag? = getCompound(name).orElse(null)

fun CompoundTag.optIntArray(name: String): IntArray? = getIntArray(name).orElse(null)

fun CompoundTag.optLongArray(name: String): LongArray? = getLongArray(name).orElse(null)

fun CompoundTag.optByteArray(name: String): ByteArray? = getByteArray(name).orElse(null)

fun CompoundTag.optByte(name: String): Byte? = getByte(name).orElse(null)

fun CompoundTag.optString(name: String): String? = getString(name).orElse(null)

fun CompoundTag.keyNames(): Set<String> = keySet()

fun ListTag.compounds(): List<CompoundTag> = compoundStream().toList()

fun Tag.compoundOrNull(): CompoundTag? = asCompound().orElse(null)

fun Tag.stringOrNull(): String? = asString().orElse(null)
