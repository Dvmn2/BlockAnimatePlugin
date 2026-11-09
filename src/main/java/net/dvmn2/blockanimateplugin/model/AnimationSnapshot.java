package net.dvmn2.blockanimateplugin.model;

import java.util.List;
import java.util.UUID;

/**
 * Immutable copy of an animation, taken on the main thread and written to disk on the storage thread.
 *
 * @param name      animation name
 * @param worldId   world UUID
 * @param worldName last known world name
 * @param region    animated region
 * @param frames    frames in playback order
 */
public record AnimationSnapshot(String name, UUID worldId, String worldName, Region region, List<Frame> frames) {

    public AnimationSnapshot {
        frames = List.copyOf(frames);
    }
}
