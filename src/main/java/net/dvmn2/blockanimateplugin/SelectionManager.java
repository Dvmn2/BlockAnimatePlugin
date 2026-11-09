package net.dvmn2.blockanimateplugin;

import net.dvmn2.blockanimateplugin.model.Point;
import net.dvmn2.blockanimateplugin.model.Region;
import net.dvmn2.blockanimateplugin.model.Selection;
import net.dvmn2.blockanimateplugin.model.SelectionStatus;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Per-player selections (two block positions). Main thread only.
 *
 * <p>Entries are removed when the player quits.
 */
public final class SelectionManager {

    private final Map<UUID, Selection> selections = new HashMap<>();

    /** Returns the player's selection; an empty one if nothing was selected yet. */
    public Selection get(UUID playerId) {
        return selections.getOrDefault(playerId, Selection.EMPTY);
    }

    public void clear(UUID playerId) {
        selections.remove(playerId);
    }

    /**
     * Sets point number 1 or 2 and tells the player what the selection looks like now.
     * Shared by the wand listener and the position commands so both report identically.
     */
    public void setAndReport(Player player, int number, Point point) {
        Selection updated = get(player.getUniqueId()).withPosition(number, point);
        selections.put(player.getUniqueId(), updated);
        player.sendMessage(Lang.get(Lang.Key.POSITION_SET, player,
                Placeholder.unparsed("number", String.valueOf(number)),
                Placeholder.unparsed("x", String.valueOf(point.x())),
                Placeholder.unparsed("y", String.valueOf(point.y())),
                Placeholder.unparsed("z", String.valueOf(point.z()))));
        if (updated.status() == SelectionStatus.COMPLETE) {
            Region region = updated.toRegion();
            player.sendMessage(Lang.get(Lang.Key.SELECTION_SIZE, player,
                    Placeholder.unparsed("x", String.valueOf(region.sizeX())),
                    Placeholder.unparsed("y", String.valueOf(region.sizeY())),
                    Placeholder.unparsed("z", String.valueOf(region.sizeZ())),
                    Placeholder.unparsed("volume", String.valueOf(region.volume()))));
        }
    }
}
