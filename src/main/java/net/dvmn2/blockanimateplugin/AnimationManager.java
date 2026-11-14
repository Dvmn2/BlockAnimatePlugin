package net.dvmn2.blockanimateplugin;

import net.dvmn2.blockanimateplugin.model.Animation;
import net.dvmn2.blockanimateplugin.model.AnimationSnapshot;
import net.dvmn2.blockanimateplugin.model.AnimationState;
import net.dvmn2.blockanimateplugin.model.CopyResult;
import net.dvmn2.blockanimateplugin.model.CreateResult;
import net.dvmn2.blockanimateplugin.model.Frame;
import net.dvmn2.blockanimateplugin.model.FrameResult;
import net.dvmn2.blockanimateplugin.model.MoveMode;
import net.dvmn2.blockanimateplugin.model.MoveResult;
import net.dvmn2.blockanimateplugin.model.PlayDirection;
import net.dvmn2.blockanimateplugin.model.PlayMode;
import net.dvmn2.blockanimateplugin.model.PlayResult;
import net.dvmn2.blockanimateplugin.model.Region;
import net.dvmn2.blockanimateplugin.model.StopResult;
import net.dvmn2.blockanimateplugin.storage.AnimationStore;
import net.dvmn2.blockanimateplugin.util.ChunkGuard;
import net.dvmn2.blockanimateplugin.util.Ticks;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Registry of animations and the single entry point for creating, editing and playing them.
 *
 * <p>Main thread only. Disk writes happen on a dedicated single-thread executor from immutable snapshots;
 * changed animations are flagged dirty and written periodically and once more, synchronously, on shutdown.
 *
 * <p>Moving and copying animations also go through this class: a move may rewrite blocks of the world in
 * slices (see {@link RelocationManager}), a copy only creates data and shares the immutable frames.
 *
 * <p>Lifecycle: {@link #load()}, {@link #start()}, {@link #onSettingsReloaded()}, {@link #shutdown()}.
 */
public final class AnimationManager {

    /** Frame index meaning "append at the end". */
    public static final int APPEND = -1;

    private static final long IO_SHUTDOWN_WAIT_SECONDS = 10L;

    /** Largest absolute X or Z coordinate the game allows for a block, blocks. */
    private static final int MAX_HORIZONTAL_BLOCKS = 30_000_000;

    private final Plugin plugin;
    private final Logger log;
    private final Supplier<BlockAnimateSettings> settings;
    private final AnimationStore store;
    private final BlockDataFactory blocks;
    private final PlaybackManager playback;
    private final CaptureManager capture;
    private final RelocationManager relocation;
    private final Map<String, Animation> animations = new TreeMap<>();
    private final Set<String> dirty = new HashSet<>();
    private final ExecutorService io = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "BlockAnimate-IO");
        thread.setDaemon(true);
        return thread;
    });
    @Nullable
    private BukkitTask autosaveTask;

    public AnimationManager(Plugin plugin, Supplier<BlockAnimateSettings> settings, AnimationStore store,
                            BlockDataFactory blocks, PlaybackManager playback, CaptureManager capture,
                            RelocationManager relocation) {
        this.plugin = plugin;
        this.log = plugin.getLogger();
        this.settings = settings;
        this.store = store;
        this.blocks = blocks;
        this.playback = playback;
        this.capture = capture;
        this.relocation = relocation;
    }

    // ---------- Lifecycle ----------

    /** Loads all stored animations. Called once from {@code onEnable}. */
    public void load() {
        for (AnimationSnapshot snapshot : store.loadAll()) {
            Animation animation = Animation.restore(sanitize(snapshot));
            animations.put(animation.getName(), animation);
        }
    }

    /** Starts the periodic tasks. */
    public void start() {
        capture.start();
        playback.start();
        relocation.start();
        scheduleAutosave();
    }

    /** Stops everything and writes all unsaved data synchronously. */
    public void shutdown() {
        if (autosaveTask != null) {
            autosaveTask.cancel();
            autosaveTask = null;
        }
        capture.shutdown();
        playback.shutdown();
        relocation.shutdown();
        io.shutdown();
        try {
            if (!io.awaitTermination(IO_SHUTDOWN_WAIT_SECONDS, TimeUnit.SECONDS)) {
                log.warning("Timed out waiting for pending animation writes");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warning("Interrupted while waiting for pending animation writes");
        }
        flushDirty(false);
    }

    /** Applies changed settings (the autosave period). */
    public void onSettingsReloaded() {
        scheduleAutosave();
    }

    // ---------- Animations ----------

    public int getCount() {
        return animations.size();
    }

    /** Returns the sorted names of all animations. */
    public List<String> names() {
        return List.copyOf(animations.keySet());
    }

    /** Finds an animation by a user-typed name. */
    @Nullable
    public Animation find(String rawName) {
        return animations.get(Animation.normalizeName(rawName));
    }

    public AnimationState getState(Animation animation) {
        if (playback.isRunning(animation.getName())) {
            return AnimationState.PLAYING;
        }
        if (relocation.isRelocating(animation.getName())) {
            return AnimationState.MOVING;
        }
        if (capture.isCapturing(animation.getName())) {
            return AnimationState.CAPTURING;
        }
        return AnimationState.IDLE;
    }

    /** Creates an empty animation for a region. Frames are added separately. */
    public CreateResult create(String rawName, World world, Region region) {
        String name = Animation.normalizeName(rawName);
        if (!Animation.NAME_PATTERN.matcher(name).matches()) {
            return CreateResult.INVALID_NAME;
        }
        if (animations.containsKey(name)) {
            return CreateResult.EXISTS;
        }
        if (region.volume() > settings.get().maxVolumeBlocks()) {
            return CreateResult.TOO_LARGE;
        }
        animations.put(name, new Animation(name, world.getUID(), world.getName(), region));
        markDirty(name);
        return CreateResult.SUCCESS;
    }

    /** Deletes an animation, stopping playback and capture. Returns false if it does not exist. */
    public boolean delete(String rawName) {
        Animation animation = find(rawName);
        if (animation == null) {
            return false;
        }
        String name = animation.getName();
        playback.stop(name);
        capture.cancel(name);
        relocation.cancel(name);
        animations.remove(name);
        dirty.remove(name);
        io.execute(() -> {
            try {
                store.delete(name);
            } catch (IOException ex) {
                log.log(Level.SEVERE, "Failed to delete animation file of '" + name + "'", ex);
            }
        });
        return true;
    }

    // ---------- Frames ----------

    /**
     * Captures the current state of the region as a new frame.
     *
     * @param index  0-based position to insert at, or {@link #APPEND}
     * @param onDone called on the main thread with the final outcome if this method returns STARTED
     */
    public FrameResult addFrame(String rawName, int index, Consumer<FrameResult> onDone) {
        Animation animation = find(rawName);
        if (animation == null) {
            return FrameResult.NOT_FOUND;
        }
        if (isBusy(animation)) {
            return FrameResult.BUSY;
        }
        if (index != APPEND && (index < 0 || index > animation.getFrameCount())) {
            return FrameResult.INVALID_INDEX;
        }
        if (animation.getFrameCount() >= settings.get().maxFrames()) {
            return FrameResult.FRAME_LIMIT;
        }
        return startCapture(animation, false, index, onDone);
    }

    /**
     * Captures the current state of the region and stores it in place of an existing frame.
     *
     * @param index  0-based index of the frame to replace
     * @param onDone called on the main thread with the final outcome if this method returns STARTED
     */
    public FrameResult replaceFrame(String rawName, int index, Consumer<FrameResult> onDone) {
        Animation animation = find(rawName);
        if (animation == null) {
            return FrameResult.NOT_FOUND;
        }
        if (isBusy(animation)) {
            return FrameResult.BUSY;
        }
        if (index < 0 || index >= animation.getFrameCount()) {
            return FrameResult.INVALID_INDEX;
        }
        return startCapture(animation, true, index, onDone);
    }

    /** Removes a frame (0-based index). */
    public FrameResult removeFrame(String rawName, int index) {
        Animation animation = find(rawName);
        if (animation == null) {
            return FrameResult.NOT_FOUND;
        }
        if (isBusy(animation)) {
            return FrameResult.BUSY;
        }
        if (index < 0 || index >= animation.getFrameCount()) {
            return FrameResult.INVALID_INDEX;
        }
        animation.removeFrame(index);
        markDirty(animation.getName());
        return FrameResult.REMOVED;
    }

    // ---------- Moving and copying ----------

    /**
     * Moves the region of an animation by an offset, optionally together with its blocks.
     *
     * <p>The offsets MUST be small enough that the coordinates do not overflow {@code int}; the command
     * restricts them. Frames are never changed.
     *
     * @param mode   {@link MoveMode#REGION_ONLY} changes only the region and finishes immediately with
     *               {@link MoveResult#MOVED}; the other modes also move the blocks of the world
     * @param onDone called on the main thread with the final outcome if this method returns STARTED
     */
    public MoveResult move(String rawName, int dx, int dy, int dz, MoveMode mode, Consumer<MoveResult> onDone) {
        Animation animation = find(rawName);
        if (animation == null) {
            return MoveResult.NOT_FOUND;
        }
        if (dx == 0 && dy == 0 && dz == 0) {
            return MoveResult.NO_CHANGE;
        }
        if (isBusy(animation)) {
            return MoveResult.BUSY;
        }
        World world = plugin.getServer().getWorld(animation.getWorldId());
        if (world == null) {
            return MoveResult.WORLD_UNAVAILABLE;
        }
        Region from = animation.getRegion();
        Region to = from.shifted(dx, dy, dz);
        if (!fits(world, to)) {
            return MoveResult.INVALID_POSITION;
        }
        if (mode == MoveMode.REGION_ONLY) {
            animation.moveRegion(to);
            markDirty(animation.getName());
            return MoveResult.MOVED;
        }
        MoveResult rejected = checkMovable(world, from, to, mode);
        if (rejected != null) {
            return rejected;
        }
        boolean started = relocation.begin(animation, to, mode,
                outcome -> completeMove(animation, to, onDone, outcome));
        return started ? MoveResult.STARTED : MoveResult.BUSY;
    }

    /**
     * Creates a new animation with the frames of an existing one, placed at another position.
     * The world is not touched. The source may be in any state.
     *
     * @param world the world of the new animation
     * @param x     X of the minimum corner of the new region
     * @param y     Y of the minimum corner of the new region
     * @param z     Z of the minimum corner of the new region
     */
    public CopyResult copy(String rawSource, String rawName, World world, int x, int y, int z) {
        Animation source = find(rawSource);
        if (source == null) {
            return CopyResult.NOT_FOUND;
        }
        String name = Animation.normalizeName(rawName);
        if (!Animation.NAME_PATTERN.matcher(name).matches()) {
            return CopyResult.INVALID_NAME;
        }
        if (animations.containsKey(name)) {
            return CopyResult.EXISTS;
        }
        Region target = source.getRegion().movedTo(x, y, z);
        if (!fits(world, target)) {
            return CopyResult.INVALID_POSITION;
        }
        animations.put(name, source.copy(name, world.getUID(), world.getName(), target));
        markDirty(name);
        return CopyResult.SUCCESS;
    }

    // ---------- Playback ----------

    /** Starts playback; the delay is in ticks, counted from the end of each frame change. */
    public PlayResult play(String rawName, PlayDirection direction, PlayMode mode, int delayTicks) {
        Animation animation = find(rawName);
        if (animation == null) {
            return PlayResult.NOT_FOUND;
        }
        if (isModifying(animation)) {
            return PlayResult.BUSY;
        }
        if (relocation.isAreaBusy(animation.getWorldId(), animation.getRegion())) {
            return PlayResult.OVERLAP;
        }
        return playback.play(animation, direction, mode, delayTicks);
    }

    /** Shows one frame (0-based index) without animating. */
    public PlayResult showFrame(String rawName, int index) {
        Animation animation = find(rawName);
        if (animation == null) {
            return PlayResult.NOT_FOUND;
        }
        if (isModifying(animation)) {
            return PlayResult.BUSY;
        }
        if (relocation.isAreaBusy(animation.getWorldId(), animation.getRegion())) {
            return PlayResult.OVERLAP;
        }
        return playback.showFrame(animation, index);
    }

    public StopResult stop(String rawName) {
        Animation animation = find(rawName);
        if (animation == null) {
            return StopResult.NOT_FOUND;
        }
        return playback.stop(animation.getName()) ? StopResult.STOPPED : StopResult.NOT_RUNNING;
    }

    /** Stops every running animation; returns how many were stopped. */
    public int stopAll() {
        return playback.stopAll();
    }

    // ---------- Internals ----------

    private boolean isBusy(Animation animation) {
        return playback.isRunning(animation.getName()) || isModifying(animation);
    }

    /** Returns true while the animation is capturing a frame or moving its blocks. */
    private boolean isModifying(Animation animation) {
        return capture.isCapturing(animation.getName()) || relocation.isRelocating(animation.getName());
    }

    /** Returns true if the region lies inside the height range and the horizontal limits of the world. */
    private static boolean fits(World world, Region region) {
        return region.minY() >= world.getMinHeight() && region.maxY() < world.getMaxHeight()
                && Math.abs((long) region.minX()) <= MAX_HORIZONTAL_BLOCKS
                && Math.abs((long) region.maxX()) <= MAX_HORIZONTAL_BLOCKS
                && Math.abs((long) region.minZ()) <= MAX_HORIZONTAL_BLOCKS
                && Math.abs((long) region.maxZ()) <= MAX_HORIZONTAL_BLOCKS;
    }

    /** Checks that blocks may be written now; returns the reason if not, otherwise null. */
    @Nullable
    private MoveResult checkMovable(World world, Region from, Region to, MoveMode mode) {
        UUID worldId = world.getUID();
        // The old region is written only when it is cleared; copying merely reads it.
        boolean clearsOld = mode == MoveMode.REMOVE;
        boolean newBusy = playback.isAreaRunning(worldId, to) || relocation.isAreaBusy(worldId, to);
        boolean oldBusy = clearsOld
                && (playback.isAreaRunning(worldId, from) || relocation.isAreaBusy(worldId, from));
        if (newBusy || oldBusy) {
            return MoveResult.OVERLAP;
        }
        if (!ChunkGuard.areLoaded(world, from) || !ChunkGuard.areLoaded(world, to)) {
            return MoveResult.CHUNKS_NOT_LOADED;
        }
        return null;
    }

    private void completeMove(Animation animation, Region to, Consumer<MoveResult> onDone, MoveResult outcome) {
        if (outcome == MoveResult.COMPLETED) {
            animation.moveRegionWithBlocks(to);
            markDirty(animation.getName());
        } else {
            // The old region may have been cleared partly, so nothing is known about it any more.
            animation.setDisplayedFrame(Animation.UNKNOWN_FRAME);
        }
        onDone.accept(outcome);
    }

    private FrameResult startCapture(Animation animation, boolean replace, int index,
                                     Consumer<FrameResult> onDone) {
        World world = plugin.getServer().getWorld(animation.getWorldId());
        if (world == null) {
            return FrameResult.WORLD_UNAVAILABLE;
        }
        if (!ChunkGuard.areLoaded(world, animation.getRegion())) {
            return FrameResult.CHUNKS_NOT_LOADED;
        }
        boolean started = capture.begin(animation,
                outcome -> completeCapture(animation, replace, index, onDone, outcome));
        return started ? FrameResult.STARTED : FrameResult.BUSY;
    }

    private void completeCapture(Animation animation, boolean replace, int index, Consumer<FrameResult> onDone,
                                 CaptureManager.Outcome outcome) {
        switch (outcome) {
            case CaptureManager.Outcome.Failed failed -> onDone.accept(failed.reason());
            case CaptureManager.Outcome.Captured captured -> {
                if (replace) {
                    animation.replaceFrame(index, captured.frame());
                } else {
                    animation.insertFrame(index == APPEND ? animation.getFrameCount() : index, captured.frame());
                }
                markDirty(animation.getName());
                onDone.accept(replace ? FrameResult.REPLACED : FrameResult.ADDED);
            }
        }
    }

    private void markDirty(String name) {
        dirty.add(name);
    }

    private void scheduleAutosave() {
        if (autosaveTask != null) {
            autosaveTask.cancel();
        }
        long period = Ticks.fromWholeSeconds(settings.get().autosaveSeconds());
        autosaveTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> flushDirty(true), period, period);
    }

    /** Takes snapshots of changed animations on the main thread and writes them. */
    private void flushDirty(boolean async) {
        if (dirty.isEmpty()) {
            return;
        }
        for (String name : dirty) {
            Animation animation = animations.get(name);
            if (animation == null) {
                continue;
            }
            AnimationSnapshot snapshot = animation.snapshot();
            if (async) {
                io.execute(() -> write(snapshot));
            } else {
                write(snapshot);
            }
        }
        dirty.clear();
    }

    private void write(AnimationSnapshot snapshot) {
        try {
            store.write(snapshot);
        } catch (IOException ex) {
            log.log(Level.SEVERE, "Failed to save animation '" + snapshot.name() + "'", ex);
        }
    }

    /** Replaces block states unknown to this game version with air, reporting each state once. */
    private AnimationSnapshot sanitize(AnimationSnapshot snapshot) {
        Set<String> reported = new HashSet<>();
        List<Frame> frames = new ArrayList<>(snapshot.frames().size());
        for (Frame frame : snapshot.frames()) {
            List<String> palette = new ArrayList<>(frame.palette().size());
            for (String state : frame.palette()) {
                if (blocks.find(state) != null) {
                    palette.add(state);
                    continue;
                }
                if (reported.add(state)) {
                    log.warning("Unknown block state '" + state + "' in animation '" + snapshot.name()
                            + "'; replaced with air");
                }
                palette.add(BlockDataFactory.AIR_STATE);
            }
            frames.add(new Frame(palette, frame.indices()));
        }
        return new AnimationSnapshot(snapshot.name(), snapshot.worldId(), snapshot.worldName(),
                snapshot.region(), frames);
    }
}
