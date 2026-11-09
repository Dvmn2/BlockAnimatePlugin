package net.dvmn2.blockanimateplugin;

import net.dvmn2.blockanimateplugin.model.Limits;
import org.bukkit.configuration.ConfigurationSection;

import java.util.logging.Logger;

/**
 * Immutable snapshot of {@code config.yml}.
 *
 * @param language            "ru", "en" or "auto"
 * @param maxVolumeBlocks     maximum number of blocks in a region
 * @param maxFrames           maximum number of frames per animation
 * @param maxRunningAnimations maximum number of simultaneously playing animations
 * @param maxBlocksPerTick    maximum number of blocks one animation changes per tick
 * @param applyPhysics        whether placed blocks trigger physics
 * @param autosaveSeconds     autosave period, seconds
 */
public record BlockAnimateSettings(
        String language,
        int maxVolumeBlocks,
        int maxFrames,
        int maxRunningAnimations,
        int maxBlocksPerTick,
        boolean applyPhysics,
        int autosaveSeconds) {

    private static final String ROOT = "settings.";

    private static final int DEFAULT_MAX_VOLUME_BLOCKS = 100_000;
    private static final int DEFAULT_MAX_FRAMES = 200;
    private static final int DEFAULT_MAX_RUNNING = 8;
    private static final int MAX_RUNNING_LIMIT = 64;
    private static final int DEFAULT_MAX_BLOCKS_PER_TICK = 4_096;
    private static final int MIN_BLOCKS_PER_TICK = 64;
    private static final int MAX_BLOCKS_PER_TICK_LIMIT = 100_000;
    private static final int DEFAULT_AUTOSAVE_SECONDS = 30;
    private static final int MIN_AUTOSAVE_SECONDS = 5;
    private static final int MAX_AUTOSAVE_SECONDS = 3_600;

    public static BlockAnimateSettings parse(ConfigurationSection config, Logger log) {
        return new BlockAnimateSettings(
                config.getString(ROOT + "language", "auto"),
                readInt(config, log, "max-volume-blocks", DEFAULT_MAX_VOLUME_BLOCKS, 1, Limits.MAX_VOLUME),
                readInt(config, log, "max-frames", DEFAULT_MAX_FRAMES, 1, Limits.MAX_FRAMES),
                readInt(config, log, "max-running-animations", DEFAULT_MAX_RUNNING, 1, MAX_RUNNING_LIMIT),
                readInt(config, log, "max-blocks-per-tick", DEFAULT_MAX_BLOCKS_PER_TICK,
                        MIN_BLOCKS_PER_TICK, MAX_BLOCKS_PER_TICK_LIMIT),
                config.getBoolean(ROOT + "apply-physics", false),
                readInt(config, log, "autosave-seconds", DEFAULT_AUTOSAVE_SECONDS,
                        MIN_AUTOSAVE_SECONDS, MAX_AUTOSAVE_SECONDS));
    }

    private static int readInt(ConfigurationSection config, Logger log, String key, int fallback, int min, int max) {
        int value = config.getInt(ROOT + key, fallback);
        if (value < min || value > max) {
            log.warning(ROOT + key + " must be in " + min + ".." + max + ", got " + value
                    + "; using " + fallback);
            return fallback;
        }
        return value;
    }
}
