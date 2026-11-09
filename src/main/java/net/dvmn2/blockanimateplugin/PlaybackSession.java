package net.dvmn2.blockanimateplugin;

import net.dvmn2.blockanimateplugin.model.Animation;
import net.dvmn2.blockanimateplugin.model.Frame;
import net.dvmn2.blockanimateplugin.model.PlayDirection;
import net.dvmn2.blockanimateplugin.model.PlayMode;
import net.dvmn2.blockanimateplugin.model.Region;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;

import java.util.List;

/**
 * State machine of one playing animation: apply a frame in slices, wait, pick the next frame.
 *
 * <p>Main thread only. The delay is counted from the moment a frame has been fully applied.
 * Frame operations on the animation are rejected while a session exists, so the frame list is stable.
 */
final class PlaybackSession {

    private static final int[] NO_CELLS = new int[0];
    private static final BlockData[] NO_STATES = new BlockData[0];

    private final Animation animation;
    private final PlayMode mode;
    private final int delayTicks;
    private final boolean single;
    private int sign;
    private int current;
    private int next;
    private int waitTicks;
    private boolean applying;
    private boolean done;
    private Frame target;
    private int[] changed = NO_CELLS;
    private int cursor;
    private BlockData[] resolved = NO_STATES;

    private PlaybackSession(Animation animation, PlayMode mode, int delayTicks, boolean single, int sign,
                            int startIndex, BlockDataFactory blocks) {
        this.animation = animation;
        this.mode = mode;
        this.delayTicks = delayTicks;
        this.single = single;
        this.sign = sign;
        beginStep(startIndex, blocks);
    }

    /** Creates a session that plays from the first frame (forward) or the last frame (backward). */
    static PlaybackSession forPlay(Animation animation, PlayDirection direction, PlayMode mode, int delayTicks,
                                   BlockDataFactory blocks) {
        int start = direction == PlayDirection.FORWARD ? 0 : animation.getFrameCount() - 1;
        return new PlaybackSession(animation, mode, delayTicks, false, direction.getStep(), start, blocks);
    }

    /** Creates a session that shows one frame and finishes. */
    static PlaybackSession forShow(Animation animation, int index, BlockDataFactory blocks) {
        return new PlaybackSession(animation, PlayMode.ONCE, 0, true, 1, index, blocks);
    }

    Animation getAnimation() {
        return animation;
    }

    boolean isApplying() {
        return applying;
    }

    boolean isDone() {
        return done;
    }

    /** Counts one tick of waiting; returns true when the wait is over and the next step may begin. */
    boolean tickWait() {
        if (waitTicks > 0) {
            waitTicks--;
        }
        return waitTicks == 0;
    }

    /** Starts applying the frame chosen when the previous step completed. */
    void beginNext(BlockDataFactory blocks) {
        beginStep(next, blocks);
    }

    /** Writes up to {@code budget} changed blocks; completes the step when none are left. */
    void apply(World world, int budget, boolean physics) {
        Region region = animation.getRegion();
        int end = (int) Math.min((long) cursor + budget, changed.length);
        for (; cursor < end; cursor++) {
            int cell = changed[cursor];
            BlockData data = resolved[target.paletteIndexAt(cell)];
            world.getBlockAt(region.blockX(cell), region.blockY(cell), region.blockZ(cell))
                    .setBlockData(data, physics);
        }
        if (cursor >= changed.length) {
            completeStep();
        }
    }

    private void beginStep(int index, BlockDataFactory blocks) {
        int displayed = animation.getDisplayedFrame();
        Frame from = displayed >= 0 && displayed < animation.getFrameCount()
                ? animation.getFrame(displayed) : null;
        current = index;
        target = animation.getFrame(index);
        changed = Frame.changedCells(from, target);
        cursor = 0;
        resolved = resolve(target.palette(), blocks);
        applying = true;
        // The world is about to diverge from every stored frame until the step completes.
        animation.setDisplayedFrame(Animation.UNKNOWN_FRAME);
    }

    private void completeStep() {
        animation.setDisplayedFrame(current);
        applying = false;
        changed = NO_CELLS;
        resolved = NO_STATES;
        next = computeNext();
        done = next < 0;
        waitTicks = delayTicks;
    }

    /** Returns the index of the next frame, or -1 when playback is over. */
    private int computeNext() {
        if (single) {
            return -1;
        }
        int count = animation.getFrameCount();
        int candidate = current + sign;
        return switch (mode) {
            case ONCE -> candidate >= 0 && candidate < count ? candidate : -1;
            case LOOP -> Math.floorMod(candidate, count);
            case PINGPONG -> {
                // At least two frames are guaranteed, so the reversed step is always in range.
                if (candidate < 0 || candidate >= count) {
                    sign = -sign;
                    candidate = current + sign;
                }
                yield candidate;
            }
        };
    }

    private static BlockData[] resolve(List<String> palette, BlockDataFactory blocks) {
        BlockData[] result = new BlockData[palette.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = blocks.getOrAir(palette.get(i));
        }
        return result;
    }
}
