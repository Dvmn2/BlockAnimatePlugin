package net.dvmn2.blockanimateplugin;

import net.dvmn2.blockanimateplugin.model.Animation;
import net.dvmn2.blockanimateplugin.model.Frame;
import net.dvmn2.blockanimateplugin.model.FrameResult;
import net.dvmn2.blockanimateplugin.model.MoveMode;
import net.dvmn2.blockanimateplugin.model.MoveResult;
import net.dvmn2.blockanimateplugin.model.Region;
import net.dvmn2.blockanimateplugin.util.ChunkGuard;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Moves the blocks of an animation region to another place in slices, so large regions never stall a tick.
 *
 * <p>Main thread only. A move has two stages. First the blocks of the old region are read into a
 * {@link Frame} by the {@link CaptureManager}. Then, in slices of {@code settings.max-blocks-per-tick},
 * the frame is written to the new region and, for {@link MoveMode#REMOVE}, the cells of the old region that
 * are not part of the new one are replaced with air. Writing comes before clearing so that an interrupted
 * move never loses blocks.
 *
 * <p>This class does not change the animation: the caller commits the new region when the callback reports
 * {@link MoveResult#COMPLETED}. A stage waits while a chunk it needs is unloaded and fails if the world
 * disappears. Moves are dropped on shutdown and are not restored after a restart.
 */
public final class RelocationManager {

    private final Plugin plugin;
    private final Supplier<BlockAnimateSettings> settings;
    private final BlockDataFactory blocks;
    private final CaptureManager capture;
    private final Map<String, Move> moves = new HashMap<>();
    @Nullable
    private BukkitTask task;

    public RelocationManager(Plugin plugin, Supplier<BlockAnimateSettings> settings, BlockDataFactory blocks,
                             CaptureManager capture) {
        this.plugin = plugin;
        this.settings = settings;
        this.blocks = blocks;
        this.capture = capture;
    }

    public void start() {
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        moves.clear();
    }

    /** Returns true while the animation is being moved, including the stage that reads its blocks. */
    public boolean isRelocating(String name) {
        return moves.containsKey(name);
    }

    /** Returns true if a running move reads or writes blocks of the region. */
    public boolean isAreaBusy(UUID worldId, Region region) {
        for (Move move : moves.values()) {
            if (move.worldId.equals(worldId) && (move.from.overlaps(region) || move.to.overlaps(region))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Starts moving the blocks of the animation region to the target region.
     *
     * @param target the new region; it MUST have the same size as the current one and MUST NOT be a no-op
     * @param mode   {@link MoveMode#KEEP} or {@link MoveMode#REMOVE}
     * @param onDone called on the main thread with {@link MoveResult#COMPLETED} or a failure; never called
     *               if cancelled
     * @return false if the animation is already being moved or captured
     */
    public boolean begin(Animation animation, Region target, MoveMode mode, Consumer<MoveResult> onDone) {
        String name = animation.getName();
        if (moves.containsKey(name)) {
            return false;
        }
        Move move = new Move(animation.getWorldId(), animation.getRegion(), target, mode, onDone);
        moves.put(name, move);
        boolean started = capture.begin(animation, outcome -> onCaptured(name, move, outcome));
        if (!started) {
            moves.remove(name);
        }
        return started;
    }

    /** Cancels a move without calling its callback. Blocks already written stay in the world. */
    public void cancel(String name) {
        if (moves.remove(name) != null) {
            capture.cancel(name);
        }
    }

    private void onCaptured(String name, Move move, CaptureManager.Outcome outcome) {
        switch (outcome) {
            case CaptureManager.Outcome.Failed failed -> {
                moves.remove(name);
                // The capture only fails when the world disappears or has too many block states.
                move.onDone.accept(failed.reason() == FrameResult.TOO_MANY_STATES
                        ? MoveResult.TOO_MANY_STATES : MoveResult.WORLD_UNAVAILABLE);
            }
            case CaptureManager.Outcome.Captured captured -> move.beginWriting(captured.frame(), blocks);
        }
    }

    private void tick() {
        if (moves.isEmpty()) {
            return;
        }
        BlockAnimateSettings current = settings.get();
        List<Runnable> completions = new ArrayList<>();
        Iterator<Move> iterator = moves.values().iterator();
        while (iterator.hasNext()) {
            Move move = iterator.next();
            if (!move.isWriting()) {
                continue;
            }
            MoveResult result = move.step(plugin.getServer(), current.maxBlocksPerTick(),
                    current.applyPhysics(), blocks);
            if (result != null) {
                iterator.remove();
                completions.add(() -> move.onDone.accept(result));
            }
        }
        // Callbacks run after iteration because they may start new moves.
        completions.forEach(Runnable::run);
    }

    /** State of one move. */
    private static final class Move {

        private final UUID worldId;
        private final Region from;
        private final Region to;
        private final MoveMode mode;
        private final Consumer<MoveResult> onDone;
        @Nullable
        private Frame frame;
        private BlockData[] resolved = new BlockData[0];
        private int pasteCursor;
        private int clearCursor;

        private Move(UUID worldId, Region from, Region to, MoveMode mode, Consumer<MoveResult> onDone) {
            this.worldId = worldId;
            this.from = from;
            this.to = to;
            this.mode = mode;
            this.onDone = onDone;
        }

        private boolean isWriting() {
            return frame != null;
        }

        private void beginWriting(Frame captured, BlockDataFactory blocks) {
            frame = captured;
            List<String> palette = captured.palette();
            resolved = new BlockData[palette.size()];
            for (int i = 0; i < resolved.length; i++) {
                resolved[i] = blocks.getOrAir(palette.get(i));
            }
        }

        /** Does one slice of work; returns the outcome when finished, otherwise null. */
        @Nullable
        private MoveResult step(Server server, int budget, boolean physics, BlockDataFactory blocks) {
            World world = server.getWorld(worldId);
            if (world == null) {
                return MoveResult.WORLD_UNAVAILABLE;
            }
            if (frame != null && pasteCursor < frame.cellCount()) {
                if (ChunkGuard.areLoaded(world, to)) {
                    paste(world, frame, budget, physics);
                }
                return null;
            }
            if (mode != MoveMode.REMOVE) {
                return MoveResult.COMPLETED;
            }
            if (!ChunkGuard.areLoaded(world, from)) {
                return null;
            }
            clear(world, budget, physics, blocks.getOrAir(BlockDataFactory.AIR_STATE));
            return clearCursor >= from.volume() ? MoveResult.COMPLETED : null;
        }

        private void paste(World world, Frame source, int budget, boolean physics) {
            int end = (int) Math.min((long) pasteCursor + budget, source.cellCount());
            for (; pasteCursor < end; pasteCursor++) {
                BlockData data = resolved[source.paletteIndexAt(pasteCursor)];
                world.getBlockAt(to.blockX(pasteCursor), to.blockY(pasteCursor), to.blockZ(pasteCursor))
                        .setBlockData(data, physics);
            }
        }

        private void clear(World world, int budget, boolean physics, BlockData air) {
            int end = (int) Math.min((long) clearCursor + budget, from.volume());
            for (; clearCursor < end; clearCursor++) {
                int x = from.blockX(clearCursor);
                int y = from.blockY(clearCursor);
                int z = from.blockZ(clearCursor);
                // Cells inside the new region hold the moved blocks and MUST stay.
                if (!to.contains(x, y, z)) {
                    world.getBlockAt(x, y, z).setBlockData(air, physics);
                }
            }
        }
    }
}
