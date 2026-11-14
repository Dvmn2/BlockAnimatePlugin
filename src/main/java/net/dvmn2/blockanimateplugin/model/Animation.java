package net.dvmn2.blockanimateplugin.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * A named region with an ordered list of frames.
 *
 * <p>Main thread only. Use {@link #snapshot()} to hand the data to another thread.
 *
 * <p>The region can be moved to another place of the same size, see {@link #moveRegion(Region)}.
 * Frames are immutable, so {@link #copy} shares them between animations.
 *
 * <p>{@code displayedFrame} tracks which frame the world is believed to show, so that switching frames
 * only touches blocks that differ. It is {@link #UNKNOWN_FRAME} when the state is not known.
 */
public final class Animation {

    /** Marks "no frame is known to be displayed". */
    public static final int UNKNOWN_FRAME = -1;

    public static final Pattern NAME_PATTERN = Pattern.compile("[a-z0-9_-]{1," + Limits.MAX_NAME_LENGTH + "}");

    private final String name;
    private final UUID worldId;
    private final String worldName;
    private Region region;
    private final List<Frame> frames = new ArrayList<>();
    private int displayedFrame = UNKNOWN_FRAME;

    public Animation(String name, UUID worldId, String worldName, Region region) {
        this.name = name;
        this.worldId = worldId;
        this.worldName = worldName;
        this.region = region;
    }

    /** Rebuilds an animation from stored data. */
    public static Animation restore(AnimationSnapshot snapshot) {
        Animation animation = new Animation(snapshot.name(), snapshot.worldId(), snapshot.worldName(),
                snapshot.region());
        animation.frames.addAll(snapshot.frames());
        return animation;
    }

    /** Lower-cases a user-typed name. */
    public static String normalizeName(String raw) {
        return raw.toLowerCase(Locale.ROOT);
    }

    public String getName() {
        return name;
    }

    public UUID getWorldId() {
        return worldId;
    }

    /** Returns the world name remembered at creation time; the live name may differ. */
    public String getWorldName() {
        return worldName;
    }

    public Region getRegion() {
        return region;
    }

    /**
     * Points the animation at another region of the same size without touching the world.
     * The world around the new region is unknown, so the displayed frame is forgotten.
     *
     * @throws IllegalArgumentException if the size differs from the current region
     */
    public void moveRegion(Region target) {
        requireSameSize(target);
        region = target;
        displayedFrame = UNKNOWN_FRAME;
    }

    /**
     * Points the animation at another region of the same size after the blocks were moved there too,
     * so the displayed frame stays valid.
     *
     * @throws IllegalArgumentException if the size differs from the current region
     */
    public void moveRegionWithBlocks(Region target) {
        requireSameSize(target);
        region = target;
    }

    /**
     * Creates an animation with the same frames in another place. The frames are shared, not duplicated.
     * The copy has no known displayed frame.
     *
     * @throws IllegalArgumentException if the size differs from the current region
     */
    public Animation copy(String newName, UUID newWorldId, String newWorldName, Region newRegion) {
        requireSameSize(newRegion);
        Animation result = new Animation(newName, newWorldId, newWorldName, newRegion);
        result.frames.addAll(frames);
        return result;
    }

    public int getFrameCount() {
        return frames.size();
    }

    public Frame getFrame(int index) {
        return frames.get(index);
    }

    public int getDisplayedFrame() {
        return displayedFrame;
    }

    public void setDisplayedFrame(int index) {
        displayedFrame = index;
    }

    /** Inserts a frame captured from the current world state, so that frame is now the displayed one. */
    public void insertFrame(int index, Frame frame) {
        frames.add(index, frame);
        displayedFrame = index;
    }

    /** Replaces a frame with one captured from the current world state. */
    public void replaceFrame(int index, Frame frame) {
        frames.set(index, frame);
        displayedFrame = index;
    }

    public void removeFrame(int index) {
        frames.remove(index);
        if (displayedFrame == index) {
            displayedFrame = UNKNOWN_FRAME;
        } else if (displayedFrame > index) {
            displayedFrame--;
        }
    }

    public AnimationSnapshot snapshot() {
        return new AnimationSnapshot(name, worldId, worldName, region, frames);
    }

    private void requireSameSize(Region other) {
        if (!region.hasSameSize(other)) {
            throw new IllegalArgumentException("The region size must not change");
        }
    }
}
