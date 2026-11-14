package net.dvmn2.blockanimateplugin.model;

/** How air cells of the moved blocks are treated at the new place. */
public enum AirMode {

    /** Air cells are written too, so they replace whatever stands at the new place. */
    OVERWRITE,

    /** Air cells are skipped, so the blocks that already stand at the new place stay. */
    SKIP
}
