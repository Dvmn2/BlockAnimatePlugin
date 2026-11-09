package net.dvmn2.blockanimateplugin;

import org.bukkit.Server;
import org.bukkit.block.data.BlockData;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Creates {@link BlockData} from block state strings and caches the results.
 *
 * <p>Main thread only. The cache is bounded by the number of block states that exist in the game.
 */
public final class BlockDataFactory {

    /** State string used in place of unknown states. */
    public static final String AIR_STATE = "minecraft:air";

    private final Server server;
    private final Map<String, BlockData> cache = new HashMap<>();

    public BlockDataFactory(Server server) {
        this.server = server;
    }

    /** Returns the block data for a state string, or null if the string is not a valid block state. */
    @Nullable
    public BlockData find(String state) {
        BlockData cached = cache.get(state);
        if (cached != null) {
            return cached;
        }
        try {
            BlockData created = server.createBlockData(state);
            cache.put(state, created);
            return created;
        } catch (IllegalArgumentException ex) {
            // The state does not exist in this game version; callers decide how to recover.
            return null;
        }
    }

    /** Returns the block data for a state string, or air if the string is not a valid block state. */
    public BlockData getOrAir(String state) {
        BlockData data = find(state);
        if (data != null) {
            return data;
        }
        BlockData air = find(AIR_STATE);
        if (air == null) {
            throw new IllegalStateException("The server does not know " + AIR_STATE);
        }
        return air;
    }
}
