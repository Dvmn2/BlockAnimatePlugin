package net.dvmn2.blockanimateplugin.model;

/**
 * Outcome of moving an animation. {@link #STARTED} means the blocks are being moved and the final outcome
 * ({@link #COMPLETED} or a failure) is delivered later; {@link #MOVED} is the immediate outcome of a
 * region-only move.
 */
public enum MoveResult {
    STARTED,
    MOVED,
    COMPLETED,
    NOT_FOUND,
    NO_CHANGE,
    BUSY,
    WORLD_UNAVAILABLE,
    INVALID_POSITION,
    OVERLAP,
    CHUNKS_NOT_LOADED,
    TOO_MANY_STATES
}
