package net.dvmn2.blockanimateplugin.model;

import org.jetbrains.annotations.Nullable;

/**
 * A player's two selection points. Immutable.
 *
 * @param pos1 first point, or null if not set
 * @param pos2 second point, or null if not set
 */
public record Selection(@Nullable Point pos1, @Nullable Point pos2) {

    public static final Selection EMPTY = new Selection(null, null);

    /** Returns a copy with point number 1 or 2 replaced. */
    public Selection withPosition(int number, Point point) {
        return number == 1 ? new Selection(point, pos2) : new Selection(pos1, point);
    }

    /** Describes whether the selection can be turned into a region. */
    public SelectionStatus status() {
        if (pos1 == null) {
            return SelectionStatus.NO_POS1;
        }
        if (pos2 == null) {
            return SelectionStatus.NO_POS2;
        }
        if (!pos1.worldId().equals(pos2.worldId())) {
            return SelectionStatus.DIFFERENT_WORLDS;
        }
        return SelectionStatus.COMPLETE;
    }

    /**
     * Returns the region spanned by both points.
     *
     * @throws IllegalStateException if {@link #status()} is not {@link SelectionStatus#COMPLETE}
     */
    public Region toRegion() {
        if (pos1 == null || pos2 == null || status() != SelectionStatus.COMPLETE) {
            throw new IllegalStateException("Selection is not complete");
        }
        return Region.of(pos1, pos2);
    }
}
