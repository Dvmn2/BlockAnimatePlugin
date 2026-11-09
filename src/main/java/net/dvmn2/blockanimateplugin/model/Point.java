package net.dvmn2.blockanimateplugin.model;

import java.util.UUID;

/**
 * A block position in a world.
 *
 * @param worldId world UUID
 * @param x       block X
 * @param y       block Y
 * @param z       block Z
 */
public record Point(UUID worldId, int x, int y, int z) {
}
