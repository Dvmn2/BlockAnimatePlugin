package net.dvmn2.blockanimateplugin;

import net.dvmn2.blockanimateplugin.model.Animation;
import net.dvmn2.blockanimateplugin.model.PlayDirection;
import net.dvmn2.blockanimateplugin.model.PlayMode;
import net.dvmn2.blockanimateplugin.model.PlayResult;
import net.dvmn2.blockanimateplugin.model.Region;
import net.dvmn2.blockanimateplugin.util.ChunkGuard;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Owns the running playback sessions and advances them once per tick.
 *
 * <p>Main thread only. At most one session exists per animation. Sessions never force-load chunks:
 * while a chunk of the region is unloaded the session simply waits. A session ends on its own when
 * playback is over, or when its world disappears.
 */
public final class PlaybackManager {

    private static final int MIN_FRAMES_FOR_LOOP = 2;

    private final Plugin plugin;
    private final Supplier<BlockAnimateSettings> settings;
    private final BlockDataFactory blocks;
    private final Map<String, PlaybackSession> sessions = new LinkedHashMap<>();
    @Nullable
    private BukkitTask task;

    public PlaybackManager(Plugin plugin, Supplier<BlockAnimateSettings> settings, BlockDataFactory blocks) {
        this.plugin = plugin;
        this.settings = settings;
        this.blocks = blocks;
    }

    public void start() {
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        sessions.clear();
    }

    public boolean isRunning(String name) {
        return sessions.containsKey(name);
    }

    /** Returns true if a running animation of the world shares at least one block with the region. */
    public boolean isAreaRunning(UUID worldId, Region region) {
        for (PlaybackSession session : sessions.values()) {
            Animation other = session.getAnimation();
            if (other.getWorldId().equals(worldId) && other.getRegion().overlaps(region)) {
                return true;
            }
        }
        return false;
    }

    /** Starts playback. The animation MUST NOT be capturing; the caller checks that. */
    public PlayResult play(Animation animation, PlayDirection direction, PlayMode mode, int delayTicks) {
        int frames = animation.getFrameCount();
        if (frames == 0) {
            return PlayResult.NO_FRAMES;
        }
        if (mode != PlayMode.ONCE && frames < MIN_FRAMES_FOR_LOOP) {
            return PlayResult.NOT_ENOUGH_FRAMES;
        }
        PlayResult rejected = checkStartable(animation);
        if (rejected != null) {
            return rejected;
        }
        sessions.put(animation.getName(),
                PlaybackSession.forPlay(animation, direction, mode, delayTicks, blocks));
        return PlayResult.STARTED;
    }

    /** Shows one frame (0-based index) and stops. The animation MUST NOT be capturing. */
    public PlayResult showFrame(Animation animation, int index) {
        if (animation.getFrameCount() == 0) {
            return PlayResult.NO_FRAMES;
        }
        if (index < 0 || index >= animation.getFrameCount()) {
            return PlayResult.INVALID_INDEX;
        }
        PlayResult rejected = checkStartable(animation);
        if (rejected != null) {
            return rejected;
        }
        sessions.put(animation.getName(), PlaybackSession.forShow(animation, index, blocks));
        return PlayResult.STARTED;
    }

    /** Stops one animation; returns false if it was not running. */
    public boolean stop(String name) {
        return sessions.remove(name) != null;
    }

    /** Stops every animation; returns how many were running. */
    public int stopAll() {
        int count = sessions.size();
        sessions.clear();
        return count;
    }

    @Nullable
    private PlayResult checkStartable(Animation animation) {
        if (sessions.containsKey(animation.getName())) {
            return PlayResult.ALREADY_RUNNING;
        }
        if (sessions.size() >= settings.get().maxRunningAnimations()) {
            return PlayResult.RUNNING_LIMIT;
        }
        if (plugin.getServer().getWorld(animation.getWorldId()) == null) {
            return PlayResult.WORLD_UNAVAILABLE;
        }
        for (PlaybackSession session : sessions.values()) {
            Animation other = session.getAnimation();
            if (other.getWorldId().equals(animation.getWorldId())
                    && other.getRegion().overlaps(animation.getRegion())) {
                return PlayResult.OVERLAP;
            }
        }
        return null;
    }

    private void tick() {
        if (sessions.isEmpty()) {
            return;
        }
        BlockAnimateSettings current = settings.get();
        sessions.values().removeIf(session -> !advance(session, current));
    }

    /** Advances one session by one tick; returns false if the session is over. */
    private boolean advance(PlaybackSession session, BlockAnimateSettings current) {
        Animation animation = session.getAnimation();
        World world = plugin.getServer().getWorld(animation.getWorldId());
        if (world == null) {
            return false;
        }
        if (!session.isApplying()) {
            if (!session.tickWait()) {
                return true;
            }
            session.beginNext(blocks);
        }
        if (ChunkGuard.areLoaded(world, animation.getRegion())) {
            session.apply(world, current.maxBlocksPerTick(), current.applyPhysics());
        }
        return !session.isDone();
    }
}
