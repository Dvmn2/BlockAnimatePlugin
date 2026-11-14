package net.dvmn2.blockanimateplugin;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.dvmn2.blockanimateplugin.command.BlockAnimateCommand;
import net.dvmn2.blockanimateplugin.storage.AnimationStore;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.function.Supplier;

/** Composition root: builds the objects, wires them together and stops them in the right order. */
public final class BlockAnimatePlugin extends JavaPlugin {

    private static final String ANIMATIONS_DIRECTORY = "animations";

    private volatile BlockAnimateSettings settings;
    private AnimationManager animations;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadSettings();
        Supplier<BlockAnimateSettings> settingsSupplier = () -> settings;

        PluginKeys keys = new PluginKeys(this);
        BlockDataFactory blocks = new BlockDataFactory(getServer());
        AnimationStore store = new AnimationStore(
                getDataFolder().toPath().resolve(ANIMATIONS_DIRECTORY), getLogger());
        CaptureManager capture = new CaptureManager(this, settingsSupplier);
        PlaybackManager playback = new PlaybackManager(this, settingsSupplier, blocks);
        RelocationManager relocation = new RelocationManager(this, settingsSupplier, blocks, capture);
        animations = new AnimationManager(this, settingsSupplier, store, blocks, playback, capture, relocation);
        animations.load();
        SelectionManager selections = new SelectionManager();
        WandFactory wands = new WandFactory(keys);

        getServer().getPluginManager().registerEvents(new BlockAnimateListener(selections, wands), this);

        BlockAnimateCommand command = new BlockAnimateCommand(this, animations, selections, wands, settingsSupplier);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register(command.build(), "Animate block structures frame by frame"));

        animations.start();
        getLogger().info("Enabled. Loaded " + animations.getCount() + " animation(s).");
    }

    @Override
    public void onDisable() {
        if (animations != null) {
            animations.shutdown();
        }
    }

    /** Re-reads config.yml and rebuilds everything derived from it. */
    public void reloadAll() {
        reloadConfig();
        reloadSettings();
        animations.onSettingsReloaded();
    }

    private void reloadSettings() {
        settings = BlockAnimateSettings.parse(getConfig(), getLogger());
        Lang.setLanguage(settings.language());
    }
}
