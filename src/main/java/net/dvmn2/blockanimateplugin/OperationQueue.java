package net.dvmn2.blockanimateplugin;

import net.dvmn2.blockanimateplugin.model.Region;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * Holds operations that could not start because their animation or area was busy, and retries them in order.
 *
 * <p>Main thread only. Once per tick every waiting operation is offered to its attempt. An attempt reports
 * {@code true} when the operation is resolved (started, or failed for good) and {@code false} while it still has
 * to wait. The queue is first-in-first-out per area: an operation never starts before an earlier waiting
 * operation of the same animation or of an overlapping area in the same world, while operations that touch
 * unrelated areas start independently. Pending operations are not persisted and are dropped on shutdown.
 *
 * <p>This class does not enforce a size limit; the caller checks {@link #size()} before adding.
 */
public final class OperationQueue {

    private final Plugin plugin;
    private final List<Pending> pending = new ArrayList<>();
    @Nullable
    private BukkitTask task;

    public OperationQueue(Plugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        pending.clear();
    }

    /** Returns the number of waiting operations. */
    public int size() {
        return pending.size();
    }

    /** Returns the number of waiting operations of one animation. */
    public int countOf(String name) {
        int count = 0;
        for (Pending operation : pending) {
            if (operation.name.equals(name)) {
                count++;
            }
        }
        return count;
    }

    /** Returns true if a waiting operation concerns the same animation or an overlapping area. */
    public boolean hasConflict(String name, UUID worldId, List<Region> areas) {
        for (Pending operation : pending) {
            if (operation.conflictsWith(name, worldId, areas)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Appends an operation.
     *
     * @param areas   every region the operation reads or writes
     * @param attempt called once per tick; returns true when the operation is resolved and must be removed
     */
    public void add(String name, UUID worldId, List<Region> areas, BooleanSupplier attempt) {
        pending.add(new Pending(name, worldId, List.copyOf(areas), attempt));
    }

    /** Removes the waiting operations of one animation without running them; returns how many were removed. */
    public int cancel(String name) {
        int before = pending.size();
        pending.removeIf(operation -> operation.name.equals(name));
        return before - pending.size();
    }

    /** Removes every waiting operation without running them; returns how many were removed. */
    public int cancelAll() {
        int count = pending.size();
        pending.clear();
        return count;
    }

    private void tick() {
        if (pending.isEmpty()) {
            return;
        }
        // Attempts may start work whose callbacks touch the queue later, so iterate over a copy.
        List<Pending> waiting = new ArrayList<>();
        for (Pending operation : List.copyOf(pending)) {
            if (isBlockedBy(operation, waiting) || !operation.attempt.getAsBoolean()) {
                waiting.add(operation);
            } else {
                pending.remove(operation);
            }
        }
    }

    /** Returns true if an earlier operation that is still waiting touches the same animation or area. */
    private static boolean isBlockedBy(Pending operation, List<Pending> earlier) {
        for (Pending other : earlier) {
            if (other.conflictsWith(operation.name, operation.worldId, operation.areas)) {
                return true;
            }
        }
        return false;
    }

    /** One waiting operation. Compared by identity. */
    private static final class Pending {

        private final String name;
        private final UUID worldId;
        private final List<Region> areas;
        private final BooleanSupplier attempt;

        private Pending(String name, UUID worldId, List<Region> areas, BooleanSupplier attempt) {
            this.name = name;
            this.worldId = worldId;
            this.areas = areas;
            this.attempt = attempt;
        }

        private boolean conflictsWith(String otherName, UUID otherWorldId, List<Region> otherAreas) {
            if (name.equals(otherName)) {
                return true;
            }
            if (!worldId.equals(otherWorldId)) {
                return false;
            }
            for (Region mine : areas) {
                for (Region theirs : otherAreas) {
                    if (mine.overlaps(theirs)) {
                        return true;
                    }
                }
            }
            return false;
        }
    }
}
