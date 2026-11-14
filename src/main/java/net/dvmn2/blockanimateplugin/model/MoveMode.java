package net.dvmn2.blockanimateplugin.model;

/** What a move does besides changing the region of the animation. */
public enum MoveMode {

    /** Only the region changes; no block of the world is touched. */
    REGION_ONLY,

    /** The blocks of the old region are copied to the new one; the old blocks stay. */
    KEEP,

    /** The blocks of the old region are moved: the old blocks outside the new region become air. */
    REMOVE
}
