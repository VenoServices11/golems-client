package dev.golemsclient;

import net.minecraft.client.gui.components.debug.DebugEntryCategory;
import net.minecraft.client.gui.components.debug.DebugScreenDisplayer;
import net.minecraft.client.gui.components.debug.DebugScreenEntry;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;

/** F3's version entry with "Golem's Client 1.0.0" on top of Minecraft's own line. */
final class BrandDebugEntry implements DebugScreenEntry {
    private final DebugScreenEntry vanilla;

    BrandDebugEntry(DebugScreenEntry vanilla) {
        this.vanilla = vanilla;
    }

    @Override
    public void display(DebugScreenDisplayer out, @Nullable Level level, @Nullable LevelChunk chunk, @Nullable LevelChunk serverChunk) {
        out.addPriorityLine("Golem's Client " + GolemsClient.version());
        vanilla.display(out, level, chunk, serverChunk);
    }

    @Override
    public boolean isAllowed(boolean reducedDebugInfo) {
        return vanilla.isAllowed(reducedDebugInfo);
    }

    @Override
    public DebugEntryCategory category() {
        return vanilla.category();
    }
}
