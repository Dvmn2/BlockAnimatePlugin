package net.dvmn2.blockanimateplugin.model;

import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One animation frame: a palette of block state strings and one palette index per region cell.
 *
 * <p>Immutable by convention: {@code indices} MUST NOT be modified after construction. Because of that
 * a frame can be handed to the storage thread without copying. Block states are kept as strings
 * (as returned by {@code BlockData#getAsString()}) so that no Bukkit object is touched off the main thread.
 *
 * @param palette distinct block state strings
 * @param indices unsigned 16-bit palette index for every cell, see {@link Region} for the cell order
 */
public record Frame(List<String> palette, short[] indices) {

    private static final int INDEX_MASK = 0xFFFF;

    public Frame {
        palette = List.copyOf(palette);
    }

    /** Returns the number of cells in the frame. */
    public int cellCount() {
        return indices.length;
    }

    /** Returns the palette index of the given cell. */
    public int paletteIndexAt(int cell) {
        return indices[cell] & INDEX_MASK;
    }

    /**
     * Computes which cells must be written to turn the world from one frame into another.
     *
     * @param from the frame currently shown, or null if the world state is unknown (all cells are returned)
     * @param to   the frame to show
     * @return ascending cell indices whose block state differs
     */
    public static int[] changedCells(@Nullable Frame from, Frame to) {
        int count = to.indices.length;
        if (from == null || from.indices.length != count) {
            int[] all = new int[count];
            for (int i = 0; i < count; i++) {
                all[i] = i;
            }
            return all;
        }
        int[] remap = from.remapTo(to);
        int[] changed = new int[count];
        int found = 0;
        for (int i = 0; i < count; i++) {
            if (remap[from.indices[i] & INDEX_MASK] != (to.indices[i] & INDEX_MASK)) {
                changed[found++] = i;
            }
        }
        return Arrays.copyOf(changed, found);
    }

    /** Maps every palette index of this frame to the index of the same state in the other palette, or -1. */
    private int[] remapTo(Frame other) {
        Map<String, Integer> lookup = new HashMap<>();
        for (int i = 0; i < other.palette.size(); i++) {
            lookup.putIfAbsent(other.palette.get(i), i);
        }
        int[] remap = new int[palette.size()];
        for (int i = 0; i < remap.length; i++) {
            remap[i] = lookup.getOrDefault(palette.get(i), -1);
        }
        return remap;
    }
}
