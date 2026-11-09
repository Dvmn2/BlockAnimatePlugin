package net.dvmn2.blockanimateplugin.model;

/** Direction in which frames are played. */
public enum PlayDirection {

    FORWARD("forward", 1),
    BACKWARD("backward", -1);

    private final String id;
    private final int step;

    PlayDirection(String id, int step) {
        this.id = id;
        this.step = step;
    }

    /** Returns the command literal of this direction. */
    public String getId() {
        return id;
    }

    /** Returns +1 for forward and -1 for backward playback. */
    public int getStep() {
        return step;
    }
}
