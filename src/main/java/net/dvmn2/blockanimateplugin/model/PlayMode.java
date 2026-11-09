package net.dvmn2.blockanimateplugin.model;

/** What happens when playback reaches the end of the frame list. */
public enum PlayMode {

    /** Play once and stop on the last shown frame. */
    ONCE("once"),
    /** Jump from the last frame back to the first one (or the opposite way when playing backward). */
    LOOP("loop"),
    /** Reverse the direction at both ends. */
    PINGPONG("pingpong");

    private final String id;

    PlayMode(String id) {
        this.id = id;
    }

    /** Returns the command literal of this mode. */
    public String getId() {
        return id;
    }
}
