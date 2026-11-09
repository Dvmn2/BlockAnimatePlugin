package net.dvmn2.blockanimateplugin;

import net.dvmn2.blockanimateplugin.model.Point;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;

/** Handles the selection wand and cleans up per-player state. Main thread only. */
public final class BlockAnimateListener implements Listener {

    private final SelectionManager selections;
    private final WandFactory wands;

    public BlockAnimateListener(SelectionManager selections, WandFactory wands) {
        this.selections = selections;
        this.wands = wands;
    }

    // Selecting only changes plugin-local state, not the game, and protection plugins often cancel
    // interactions inside claims, so cancelled events are deliberately not skipped here.
    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        int number = switch (event.getAction()) {
            case LEFT_CLICK_BLOCK -> 1;
            case RIGHT_CLICK_BLOCK -> 2;
            default -> 0;
        };
        Block block = event.getClickedBlock();
        if (number == 0 || block == null || !wands.isWand(event.getItem())) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.hasPermission(Permissions.SELECT)) {
            return;
        }
        event.setCancelled(true);
        selections.setAndReport(player, number,
                new Point(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ()));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        selections.clear(event.getPlayer().getUniqueId());
    }
}
