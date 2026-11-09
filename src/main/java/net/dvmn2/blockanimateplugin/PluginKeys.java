package net.dvmn2.blockanimateplugin;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/** Factory of the persistent data keys used by the plugin. Built from the plugin instance. */
public final class PluginKeys {

    private final NamespacedKey wand;

    public PluginKeys(Plugin plugin) {
        this.wand = new NamespacedKey(plugin, "wand");
    }

    /** Marks the selection wand item. */
    public NamespacedKey wand() {
        return wand;
    }
}
