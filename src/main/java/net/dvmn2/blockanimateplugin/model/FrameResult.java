package net.dvmn2.blockanimateplugin.model;

/**
 * Outcome of a frame operation. {@link #STARTED} means a capture was started and the final outcome
 * ({@link #ADDED}, {@link #REPLACED} or a failure) is delivered later. {@link #QUEUED} means the animation is
 * busy and the operation waits in the queue; the outcome of its deferred start follows, then the final outcome.
 */
public enum FrameResult {
    STARTED,
    ADDED,
    REPLACED,
    REMOVED,
    NOT_FOUND,
    BUSY,
    INVALID_INDEX,
    FRAME_LIMIT,
    WORLD_UNAVAILABLE,
    CHUNKS_NOT_LOADED,
    TOO_MANY_STATES,
    QUEUED,
    QUEUE_FULL
}
