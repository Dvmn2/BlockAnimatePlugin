package net.dvmn2.blockanimateplugin.model;

/** Hard limits shared by settings validation and the data file reader. */
public final class Limits {

    /** Absolute upper bound for the number of blocks in one region. */
    public static final int MAX_VOLUME = 1_000_000;

    /** Absolute upper bound for the number of frames in one animation. */
    public static final int MAX_FRAMES = 1_000;

    /** Palette entries are addressed by an unsigned 16-bit index. */
    public static final int MAX_PALETTE = 65_535;

    /** Maximum length of an animation name, characters. */
    public static final int MAX_NAME_LENGTH = 32;

    private Limits() {
    }
}
