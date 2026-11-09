package net.dvmn2.blockanimateplugin.util;

import net.dvmn2.blockanimateplugin.model.Region;
import org.bukkit.World;

/** Checks that touching a region will not force synchronous chunk loading. Main thread only. */
public final class ChunkGuard {

    private ChunkGuard() {
    }

    /** Returns true if every chunk overlapping the region is currently loaded. */
    public static boolean areLoaded(World world, Region region) {
        for (int chunkX = region.minChunkX(); chunkX <= region.maxChunkX(); chunkX++) {
            for (int chunkZ = region.minChunkZ(); chunkZ <= region.maxChunkZ(); chunkZ++) {
                if (!world.isChunkLoaded(chunkX, chunkZ)) {
                    return false;
                }
            }
        }
        return true;
    }
}
