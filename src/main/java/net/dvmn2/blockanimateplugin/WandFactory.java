package net.dvmn2.blockanimateplugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Creates and recognises the selection wand. The wand is marked with a persistent data key. */
public final class WandFactory {

    private static final Material MATERIAL = Material.BLAZE_ROD;
    private static final byte WAND_MARK = 1;

    private final PluginKeys keys;

    public WandFactory(PluginKeys keys) {
        this.keys = keys;
    }

    /** Creates a wand named and described in the viewer's language. */
    public ItemStack create(Player viewer) {
        ItemStack item = new ItemStack(MATERIAL);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(plain(Lang.get(Lang.Key.WAND_NAME, viewer)));
        meta.lore(List.of(
                plain(Lang.get(Lang.Key.WAND_LORE_LEFT, viewer)),
                plain(Lang.get(Lang.Key.WAND_LORE_RIGHT, viewer))));
        meta.getPersistentDataContainer().set(keys.wand(), PersistentDataType.BYTE, WAND_MARK);
        item.setItemMeta(meta);
        return item;
    }

    /** Returns true if the item is a selection wand. */
    public boolean isWand(@Nullable ItemStack item) {
        if (item == null || item.getType() != MATERIAL) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(keys.wand(), PersistentDataType.BYTE);
    }

    /** Item names and lore are italic by default; the wand text must not be. */
    private static Component plain(Component component) {
        return component.decoration(TextDecoration.ITALIC, false);
    }
}
