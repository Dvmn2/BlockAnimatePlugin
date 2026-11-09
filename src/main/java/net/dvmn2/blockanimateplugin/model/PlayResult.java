package net.dvmn2.blockanimateplugin.model;

/** Outcome of starting playback or showing a single frame. */
public enum PlayResult {
    STARTED,
    NOT_FOUND,
    BUSY,
    ALREADY_RUNNING,
    NO_FRAMES,
    NOT_ENOUGH_FRAMES,
    INVALID_INDEX,
    WORLD_UNAVAILABLE,
    RUNNING_LIMIT,
    OVERLAP
}
