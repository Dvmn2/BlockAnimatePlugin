package net.dvmn2.blockanimateplugin.model;

/**
 * Inclusive axis-aligned box of blocks. Corners are normalised so that min is never greater than max.
 *
 * <p>Blocks are addressed by a linear cell index: X changes fastest, then Z, then Y. The index mapping
 * is only meaningful for regions whose volume does not exceed {@link Limits#MAX_VOLUME}.
 *
 * <p>Immutable; safe to use from any thread.
 */
public record Region(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

    private static final int CHUNK_SHIFT = 4;

    public Region {
        int loX = Math.min(minX, maxX);
        int hiX = Math.max(minX, maxX);
        int loY = Math.min(minY, maxY);
        int hiY = Math.max(minY, maxY);
        int loZ = Math.min(minZ, maxZ);
        int hiZ = Math.max(minZ, maxZ);
        minX = loX;
        maxX = hiX;
        minY = loY;
        maxY = hiY;
        minZ = loZ;
        maxZ = hiZ;
    }

    /** Creates the region spanned by two points. The world of the points is not checked. */
    public static Region of(Point a, Point b) {
        return new Region(a.x(), a.y(), a.z(), b.x(), b.y(), b.z());
    }

    public int sizeX() {
        return span(minX, maxX);
    }

    public int sizeY() {
        return span(minY, maxY);
    }

    public int sizeZ() {
        return span(minZ, maxZ);
    }

    /** Returns the number of blocks, saturating at {@link Long#MAX_VALUE} for absurdly large boxes. */
    public long volume() {
        double product = (double) sizeX() * sizeY() * sizeZ();
        return product >= Long.MAX_VALUE ? Long.MAX_VALUE : (long) product;
    }

    /** Returns true if the two boxes share at least one block. */
    public boolean overlaps(Region other) {
        return minX <= other.maxX && maxX >= other.minX
                && minY <= other.maxY && maxY >= other.minY
                && minZ <= other.maxZ && maxZ >= other.minZ;
    }

    /** Returns the world X of the block with the given cell index. */
    public int blockX(int cell) {
        return minX + cell % sizeX();
    }

    /** Returns the world Y of the block with the given cell index. */
    public int blockY(int cell) {
        return minY + cell / (sizeX() * sizeZ());
    }

    /** Returns the world Z of the block with the given cell index. */
    public int blockZ(int cell) {
        return minZ + (cell / sizeX()) % sizeZ();
    }

    public int minChunkX() {
        return minX >> CHUNK_SHIFT;
    }

    public int maxChunkX() {
        return maxX >> CHUNK_SHIFT;
    }

    public int minChunkZ() {
        return minZ >> CHUNK_SHIFT;
    }

    public int maxChunkZ() {
        return maxZ >> CHUNK_SHIFT;
    }

    private static int span(int min, int max) {
        return (int) Math.min((long) max - min + 1L, Integer.MAX_VALUE);
    }
}
