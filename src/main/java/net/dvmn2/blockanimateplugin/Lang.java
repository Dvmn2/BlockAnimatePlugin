package net.dvmn2.blockanimateplugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * All player-visible texts in Russian and English (Adventure MiniMessage).
 *
 * <p>The only static mutable state of the plugin is the current language.
 * Player data MUST be passed through {@link TagResolver}s, never concatenated into a template.
 */
public final class Lang {

    public enum Key {
        ONLY_PLAYERS,
        RELOADED,
        WAND_GIVEN,
        WAND_NAME,
        WAND_LORE_LEFT,
        WAND_LORE_RIGHT,
        POSITION_SET,
        SELECTION_SIZE,
        INVALID_POSITION,
        SELECTION_NO_POS1,
        SELECTION_NO_POS2,
        SELECTION_DIFFERENT_WORLDS,
        CREATE_SUCCESS,
        CREATE_INVALID_NAME,
        CREATE_EXISTS,
        CREATE_TOO_LARGE,
        NOT_FOUND,
        DELETED,
        LIST_EMPTY,
        LIST_HEADER,
        LIST_ENTRY,
        INFO_HEADER,
        INFO_WORLD,
        INFO_REGION,
        INFO_FRAMES,
        INFO_STATE,
        STATE_IDLE,
        STATE_PLAYING,
        STATE_CAPTURING,
        FRAME_CAPTURING,
        FRAME_ADDED,
        FRAME_REPLACED,
        FRAME_REMOVED,
        FRAME_INVALID_INDEX,
        FRAME_LIMIT,
        BUSY,
        WORLD_UNAVAILABLE,
        CHUNKS_NOT_LOADED,
        TOO_MANY_STATES,
        SHOW_STARTED,
        PLAY_STARTED,
        PLAY_NO_FRAMES,
        PLAY_NOT_ENOUGH_FRAMES,
        PLAY_ALREADY_RUNNING,
        PLAY_RUNNING_LIMIT,
        PLAY_OVERLAP,
        STOPPED,
        STOPPED_ALL,
        NOT_RUNNING,
        NOTHING_RUNNING
    }

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final Map<Key, String> RU = new EnumMap<>(Key.class);
    private static final Map<Key, String> EN = new EnumMap<>(Key.class);

    private static volatile String language = "auto";

    static {
        put(Key.ONLY_PLAYERS,
                "<red>Эту команду могут использовать только игроки.",
                "<red>This command can only be used by players.");
        put(Key.RELOADED,
                "<green>Конфигурация перезагружена.",
                "<green>Configuration reloaded.");

        put(Key.WAND_GIVEN,
                "<green>Вы получили палочку выделения.",
                "<green>You received the selection wand.");
        put(Key.WAND_NAME,
                "<gold>Палочка Block Animate",
                "<gold>Block Animate Wand");
        put(Key.WAND_LORE_LEFT,
                "<gray>ЛКМ по блоку: точка 1",
                "<gray>Left click a block: position 1");
        put(Key.WAND_LORE_RIGHT,
                "<gray>ПКМ по блоку: точка 2",
                "<gray>Right click a block: position 2");
        put(Key.POSITION_SET,
                "<green>Точка <number> установлена: <yellow><x>, <y>, <z></yellow>.",
                "<green>Position <number> set to <yellow><x>, <y>, <z></yellow>.");
        put(Key.SELECTION_SIZE,
                "<gray>Выделено: <yellow><x> x <y> x <z></yellow> (блоков: <volume>).",
                "<gray>Selection: <yellow><x> x <y> x <z></yellow> (<volume> blocks).");
        put(Key.INVALID_POSITION,
                "<red>Недопустимая позиция: она должна быть в пределах высоты мира.",
                "<red>Invalid position: it must be inside the world height range.");
        put(Key.SELECTION_NO_POS1,
                "<red>Сначала установите точку 1.",
                "<red>Set position 1 first.");
        put(Key.SELECTION_NO_POS2,
                "<red>Сначала установите точку 2.",
                "<red>Set position 2 first.");
        put(Key.SELECTION_DIFFERENT_WORLDS,
                "<red>Точки выделения находятся в разных мирах.",
                "<red>The selection points are in different worlds.");

        put(Key.CREATE_SUCCESS,
                "<green>Анимация <yellow><name></yellow> создана (блоков: <volume>). "
                        + "Добавьте кадры: <yellow>/blockanimate frame add <name></yellow>.",
                "<green>Animation <yellow><name></yellow> created (<volume> blocks). "
                        + "Add frames with <yellow>/blockanimate frame add <name></yellow>.");
        put(Key.CREATE_INVALID_NAME,
                "<red>Недопустимое имя. Используйте от 1 до <limit> символов: a-z, 0-9, _ и -.",
                "<red>Invalid name. Use 1 to <limit> characters: a-z, 0-9, _ and -.");
        put(Key.CREATE_EXISTS,
                "<red>Анимация <yellow><name></yellow> уже существует.",
                "<red>Animation <yellow><name></yellow> already exists.");
        put(Key.CREATE_TOO_LARGE,
                "<red>Область слишком большая: блоков <volume> (лимит: <limit>).",
                "<red>The region is too large: <volume> blocks (limit: <limit>).");
        put(Key.NOT_FOUND,
                "<red>Анимация <yellow><name></yellow> не найдена.",
                "<red>Animation <yellow><name></yellow> not found.");
        put(Key.DELETED,
                "<green>Анимация <yellow><name></yellow> удалена.",
                "<green>Animation <yellow><name></yellow> deleted.");

        put(Key.LIST_EMPTY,
                "<gray>Анимаций пока нет.",
                "<gray>No animations yet.");
        put(Key.LIST_HEADER,
                "<gold>Анимации (<count>):",
                "<gold>Animations (<count>):");
        put(Key.LIST_ENTRY,
                "<gray>- <yellow><name></yellow>: кадров <frames>, блоков <volume>, <state>",
                "<gray>- <yellow><name></yellow>: <frames> frame(s), <volume> blocks, <state>");
        put(Key.INFO_HEADER,
                "<gold>Анимация <yellow><name></yellow>",
                "<gold>Animation <yellow><name></yellow>");
        put(Key.INFO_WORLD,
                "<gray>Мир: <white><world>",
                "<gray>World: <white><world>");
        put(Key.INFO_REGION,
                "<gray>Область: <white><min></white> - <white><max></white> "
                        + "(<size>, блоков: <volume>)",
                "<gray>Region: <white><min></white> - <white><max></white> "
                        + "(<size>, <volume> blocks)");
        put(Key.INFO_FRAMES,
                "<gray>Кадров: <white><count>",
                "<gray>Frames: <white><count>");
        put(Key.INFO_STATE,
                "<gray>Состояние: <state>",
                "<gray>State: <state>");
        put(Key.STATE_IDLE,
                "<gray>ожидает",
                "<gray>idle");
        put(Key.STATE_PLAYING,
                "<green>воспроизводится",
                "<green>playing");
        put(Key.STATE_CAPTURING,
                "<yellow>снимает кадр",
                "<yellow>capturing");

        put(Key.FRAME_CAPTURING,
                "<gray>Снимаю кадр...",
                "<gray>Capturing the frame...");
        put(Key.FRAME_ADDED,
                "<green>Кадр <index> добавлен в <yellow><name></yellow> (всего: <count>).",
                "<green>Frame <index> added to <yellow><name></yellow> (total: <count>).");
        put(Key.FRAME_REPLACED,
                "<green>Кадр <index> анимации <yellow><name></yellow> заменён.",
                "<green>Frame <index> of <yellow><name></yellow> replaced.");
        put(Key.FRAME_REMOVED,
                "<green>Кадр <index> удалён из <yellow><name></yellow> (всего: <count>).",
                "<green>Frame <index> removed from <yellow><name></yellow> (total: <count>).");
        put(Key.FRAME_INVALID_INDEX,
                "<red>Недопустимый номер кадра. Допустимо: <min>..<max>.",
                "<red>Invalid frame number. Valid range: <min>..<max>.");
        put(Key.FRAME_LIMIT,
                "<red>Достигнут лимит кадров (<limit>).",
                "<red>The frame limit is reached (<limit>).");
        put(Key.BUSY,
                "<red>Анимация <yellow><name></yellow> занята (воспроизводится или снимает кадр).",
                "<red>Animation <yellow><name></yellow> is busy (playing or capturing a frame).");
        put(Key.WORLD_UNAVAILABLE,
                "<red>Мир этой анимации не загружен.",
                "<red>The world of this animation is not loaded.");
        put(Key.CHUNKS_NOT_LOADED,
                "<red>Часть чанков области не загружена. Подойдите ближе и повторите.",
                "<red>Some chunks of the region are not loaded. Move closer and try again.");
        put(Key.TOO_MANY_STATES,
                "<red>В области слишком много разных состояний блоков.",
                "<red>The region contains too many different block states.");

        put(Key.SHOW_STARTED,
                "<green>Показываю кадр <index> анимации <yellow><name></yellow>.",
                "<green>Showing frame <index> of <yellow><name></yellow>.");
        put(Key.PLAY_STARTED,
                "<green>Запущена анимация <yellow><name></yellow>: <direction>, <mode>, "
                        + "задержка <delay> с.",
                "<green>Started <yellow><name></yellow>: <direction>, <mode>, delay <delay> s.");
        put(Key.PLAY_NO_FRAMES,
                "<red>У анимации <yellow><name></yellow> нет кадров.",
                "<red>Animation <yellow><name></yellow> has no frames.");
        put(Key.PLAY_NOT_ENOUGH_FRAMES,
                "<red>Режимам loop и pingpong нужно минимум 2 кадра.",
                "<red>Modes loop and pingpong need at least 2 frames.");
        put(Key.PLAY_ALREADY_RUNNING,
                "<red>Анимация <yellow><name></yellow> уже воспроизводится.",
                "<red>Animation <yellow><name></yellow> is already playing.");
        put(Key.PLAY_RUNNING_LIMIT,
                "<red>Запущено слишком много анимаций (лимит: <limit>).",
                "<red>Too many animations are running (limit: <limit>).");
        put(Key.PLAY_OVERLAP,
                "<red>Область пересекается с уже воспроизводимой анимацией.",
                "<red>The region overlaps with a running animation.");
        put(Key.STOPPED,
                "<green>Анимация <yellow><name></yellow> остановлена.",
                "<green>Animation <yellow><name></yellow> stopped.");
        put(Key.STOPPED_ALL,
                "<green>Остановлено анимаций: <count>.",
                "<green>Stopped animations: <count>.");
        put(Key.NOT_RUNNING,
                "<red>Анимация <yellow><name></yellow> не воспроизводится.",
                "<red>Animation <yellow><name></yellow> is not playing.");
        put(Key.NOTHING_RUNNING,
                "<gray>Нет воспроизводимых анимаций.",
                "<gray>No animations are running.");
        verify();
    }

    private Lang() {
    }

    private static void put(Key key, String ru, String en) {
        RU.put(key, ru);
        EN.put(key, en);
    }

    private static void verify() {
        for (Key key : Key.values()) {
            if (!RU.containsKey(key) || !EN.containsKey(key)) {
                throw new IllegalStateException("Missing translation for " + key);
            }
        }
    }

    /** Sets the language: "ru", "en" or "auto". */
    public static void setLanguage(String value) {
        language = value == null || value.isBlank() ? "auto" : value.toLowerCase(Locale.ROOT);
    }

    public static Component get(Key key, CommandSender sender, TagResolver... resolvers) {
        return MM.deserialize(table(sender).get(key), resolvers);
    }

    private static Map<Key, String> table(CommandSender sender) {
        return switch (language) {
            case "ru" -> RU;
            case "en" -> EN;
            default -> sender instanceof Player player
                    && "ru".equalsIgnoreCase(player.locale().getLanguage()) ? RU : EN;
        };
    }
}
