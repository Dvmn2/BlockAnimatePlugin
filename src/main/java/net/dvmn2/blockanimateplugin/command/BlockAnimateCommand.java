package net.dvmn2.blockanimateplugin.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.BlockPositionResolver;
import io.papermc.paper.math.BlockPosition;
import net.dvmn2.blockanimateplugin.AnimationManager;
import net.dvmn2.blockanimateplugin.BlockAnimatePlugin;
import net.dvmn2.blockanimateplugin.BlockAnimateSettings;
import net.dvmn2.blockanimateplugin.Lang;
import net.dvmn2.blockanimateplugin.Permissions;
import net.dvmn2.blockanimateplugin.SelectionManager;
import net.dvmn2.blockanimateplugin.WandFactory;
import net.dvmn2.blockanimateplugin.model.AirMode;
import net.dvmn2.blockanimateplugin.model.Animation;
import net.dvmn2.blockanimateplugin.model.CopyResult;
import net.dvmn2.blockanimateplugin.model.CreateResult;
import net.dvmn2.blockanimateplugin.model.FrameResult;
import net.dvmn2.blockanimateplugin.model.Limits;
import net.dvmn2.blockanimateplugin.model.MoveMode;
import net.dvmn2.blockanimateplugin.model.MoveResult;
import net.dvmn2.blockanimateplugin.model.PlayDirection;
import net.dvmn2.blockanimateplugin.model.PlayMode;
import net.dvmn2.blockanimateplugin.model.PlayResult;
import net.dvmn2.blockanimateplugin.model.Point;
import net.dvmn2.blockanimateplugin.model.Region;
import net.dvmn2.blockanimateplugin.model.Selection;
import net.dvmn2.blockanimateplugin.model.SelectionStatus;
import net.dvmn2.blockanimateplugin.model.StopResult;
import net.dvmn2.blockanimateplugin.util.Ticks;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * The {@code /blockanimate} command tree.
 *
 * <pre>
 * /blockanimate
 * ├── wand
 * ├── pos1 [&lt;position&gt;]
 * ├── pos2 [&lt;position&gt;]
 * ├── create &lt;name&gt;
 * ├── delete &lt;name&gt;
 * ├── list
 * ├── info &lt;name&gt;
 * ├── move &lt;name&gt; &lt;dx&gt; &lt;dy&gt; &lt;dz&gt; [blocks (keep|remove) [air|skip-air]]
 * ├── copy &lt;name&gt; &lt;new-name&gt; [&lt;position&gt;] [show &lt;index&gt;]
 * ├── frame
 * │   ├── add &lt;name&gt; [&lt;index&gt;]
 * │   ├── replace &lt;name&gt; &lt;index&gt;
 * │   ├── remove &lt;name&gt; &lt;index&gt;
 * │   └── show &lt;name&gt; &lt;index&gt;
 * ├── play &lt;name&gt; (forward|backward) &lt;delay&gt; [once|loop|pingpong]
 * ├── stop [&lt;name&gt;]
 * └── reload
 * </pre>
 *
 * <p>Frame numbers are 1-based, delays are in seconds (fractions allowed, the minimum is one tick).
 * Offsets of {@code move} are in blocks; {@code air} (the default) lets air cells of the moved blocks overwrite
 * the new place, {@code skip-air} leaves what stands there. {@code copy} places the minimum corner of the new region at
 * {@code position}, or at position 1 of the player's selection when no position is given.
 */
public final class BlockAnimateCommand {

    private static final String ROOT = "blockanimate";
    private static final String ARG_NAME = "name";
    private static final String ARG_POSITION = "position";
    private static final String ARG_INDEX = "index";
    private static final String ARG_DELAY = "delay";
    private static final String ARG_DX = "dx";
    private static final String ARG_DY = "dy";
    private static final String ARG_DZ = "dz";
    private static final String ARG_NEW_NAME = "new-name";

    /** One tick, seconds. */
    private static final double MIN_DELAY_SECONDS = 0.05;
    private static final double MAX_DELAY_SECONDS = 3_600.0;
    private static final int MAX_INDEX_SUGGESTIONS = 100;
    private static final int FIRST_FRAME_NUMBER = 1;
    /** Largest absolute offset of {@code move}, blocks; keeps the shifted coordinates inside {@code int}. */
    private static final int MAX_OFFSET = 60_000_000;
    /** Marks "do not show any frame after copying". */
    private static final int NO_FRAME = -1;

    /** Where a copy is placed. */
    private record Destination(World world, int x, int y, int z) {
    }

    private final BlockAnimatePlugin plugin;
    private final AnimationManager animations;
    private final SelectionManager selections;
    private final WandFactory wands;
    private final Supplier<BlockAnimateSettings> settings;

    public BlockAnimateCommand(BlockAnimatePlugin plugin, AnimationManager animations, SelectionManager selections,
                               WandFactory wands, Supplier<BlockAnimateSettings> settings) {
        this.plugin = plugin;
        this.animations = animations;
        this.selections = selections;
        this.wands = wands;
        this.settings = settings;
    }

    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal(ROOT)
                .requires(has(Permissions.USE))
                .then(Commands.literal("wand")
                        .requires(has(Permissions.SELECT))
                        .executes(this::wand))
                .then(positionNode("pos1", 1))
                .then(positionNode("pos2", 2))
                .then(Commands.literal("create")
                        .requires(has(Permissions.EDIT))
                        .then(Commands.argument(ARG_NAME, StringArgumentType.word())
                                .executes(this::create)))
                .then(Commands.literal("delete")
                        .requires(has(Permissions.EDIT))
                        .then(animationArgument().executes(this::delete)))
                .then(Commands.literal("list").executes(this::list))
                .then(Commands.literal("info")
                        .then(animationArgument().executes(this::info)))
                .then(moveNode())
                .then(copyNode())
                .then(frameNode())
                .then(playNode())
                .then(Commands.literal("stop")
                        .requires(has(Permissions.PLAY))
                        .executes(this::stopAll)
                        .then(animationArgument().executes(this::stop)))
                .then(Commands.literal("reload")
                        .requires(has(Permissions.RELOAD))
                        .executes(this::reload))
                .build();
    }

    // ---------- Tree parts ----------

    private LiteralArgumentBuilder<CommandSourceStack> positionNode(String literal, int number) {
        return Commands.literal(literal)
                .requires(has(Permissions.SELECT))
                .executes(ctx -> setPosition(ctx, number, false))
                .then(Commands.argument(ARG_POSITION, ArgumentTypes.blockPosition())
                        .executes(ctx -> setPosition(ctx, number, true)));
    }

    private LiteralArgumentBuilder<CommandSourceStack> moveNode() {
        return Commands.literal("move")
                .requires(has(Permissions.EDIT))
                .then(animationArgument()
                        .then(offsetArgument(ARG_DX)
                                .then(offsetArgument(ARG_DY)
                                        .then(offsetArgument(ARG_DZ)
                                                .executes(ctx -> move(ctx, MoveMode.REGION_ONLY, AirMode.OVERWRITE))
                                                .then(Commands.literal("blocks")
                                                        .requires(has(Permissions.PLAY))
                                                        .then(blocksModeNode("keep", MoveMode.KEEP))
                                                        .then(blocksModeNode("remove", MoveMode.REMOVE)))))));
    }

    /** One of {@code keep} / {@code remove} with its optional {@code air} / {@code skip-air} tail. */
    private LiteralArgumentBuilder<CommandSourceStack> blocksModeNode(String literal, MoveMode mode) {
        return Commands.literal(literal)
                .executes(ctx -> move(ctx, mode, AirMode.OVERWRITE))
                .then(Commands.literal("air").executes(ctx -> move(ctx, mode, AirMode.OVERWRITE)))
                .then(Commands.literal("skip-air").executes(ctx -> move(ctx, mode, AirMode.SKIP)));
    }

    private LiteralArgumentBuilder<CommandSourceStack> copyNode() {
        return Commands.literal("copy")
                .requires(has(Permissions.EDIT))
                .then(animationArgument()
                        .then(Commands.argument(ARG_NEW_NAME, StringArgumentType.word())
                                .executes(ctx -> copy(ctx, false, false))
                                .then(showNode(false))
                                .then(Commands.argument(ARG_POSITION, ArgumentTypes.blockPosition())
                                        .executes(ctx -> copy(ctx, true, false))
                                        .then(showNode(true)))));
    }

    /** The optional {@code show <index>} tail of {@code copy}. */
    private LiteralArgumentBuilder<CommandSourceStack> showNode(boolean explicitPosition) {
        return Commands.literal("show")
                .requires(has(Permissions.PLAY))
                .then(indexArgument(false).executes(ctx -> copy(ctx, explicitPosition, true)));
    }

    private LiteralArgumentBuilder<CommandSourceStack> frameNode() {
        return Commands.literal("frame")
                .then(Commands.literal("add")
                        .requires(has(Permissions.EDIT))
                        .then(animationArgument()
                                .executes(ctx -> frameAdd(ctx, AnimationManager.APPEND))
                                .then(indexArgument(true)
                                        .executes(ctx -> frameAdd(ctx, indexArg(ctx))))))
                .then(Commands.literal("replace")
                        .requires(has(Permissions.EDIT))
                        .then(animationArgument()
                                .then(indexArgument(false).executes(this::frameReplace))))
                .then(Commands.literal("remove")
                        .requires(has(Permissions.EDIT))
                        .then(animationArgument()
                                .then(indexArgument(false).executes(this::frameRemove))))
                .then(Commands.literal("show")
                        .requires(has(Permissions.PLAY))
                        .then(animationArgument()
                                .then(indexArgument(false).executes(this::frameShow))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> playNode() {
        RequiredArgumentBuilder<CommandSourceStack, String> name = animationArgument();
        for (PlayDirection direction : PlayDirection.values()) {
            RequiredArgumentBuilder<CommandSourceStack, Double> delay = Commands
                    .argument(ARG_DELAY, DoubleArgumentType.doubleArg(MIN_DELAY_SECONDS, MAX_DELAY_SECONDS))
                    .executes(ctx -> play(ctx, direction, PlayMode.ONCE));
            for (PlayMode mode : PlayMode.values()) {
                delay.then(Commands.literal(mode.getId()).executes(ctx -> play(ctx, direction, mode)));
            }
            name.then(Commands.literal(direction.getId()).then(delay));
        }
        return Commands.literal("play")
                .requires(has(Permissions.PLAY))
                .then(name);
    }

    private RequiredArgumentBuilder<CommandSourceStack, String> animationArgument() {
        return Commands.argument(ARG_NAME, StringArgumentType.word()).suggests(this::suggestNames);
    }

    private static RequiredArgumentBuilder<CommandSourceStack, Integer> offsetArgument(String argument) {
        return Commands.argument(argument, IntegerArgumentType.integer(-MAX_OFFSET, MAX_OFFSET));
    }

    private RequiredArgumentBuilder<CommandSourceStack, Integer> indexArgument(boolean allowNext) {
        return Commands.argument(ARG_INDEX, IntegerArgumentType.integer(FIRST_FRAME_NUMBER, Limits.MAX_FRAMES + 1))
                .suggests((ctx, builder) -> suggestIndices(ctx, builder, allowNext));
    }

    // ---------- Selection ----------

    private int wand(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        Player player = requirePlayer(ctx);
        if (player == null) {
            return 0;
        }
        ItemStack wand = wands.create(player);
        for (ItemStack leftover : player.getInventory().addItem(wand).values()) {
            player.getWorld().dropItem(player.getLocation(), leftover);
        }
        sender.sendMessage(Lang.get(Lang.Key.WAND_GIVEN, sender));
        return Command.SINGLE_SUCCESS;
    }

    private int setPosition(CommandContext<CommandSourceStack> ctx, int number, boolean explicit) {
        CommandSender sender = ctx.getSource().getSender();
        Player player = requirePlayer(ctx);
        if (player == null) {
            return 0;
        }
        int x;
        int y;
        int z;
        if (explicit) {
            try {
                BlockPosition position = ctx.getArgument(ARG_POSITION, BlockPositionResolver.class)
                        .resolve(ctx.getSource());
                x = position.blockX();
                y = position.blockY();
                z = position.blockZ();
            } catch (CommandSyntaxException ex) {
                sender.sendMessage(Lang.get(Lang.Key.INVALID_POSITION, sender));
                return 0;
            }
        } else {
            // Default is the block the player stands on.
            Location location = player.getLocation();
            x = location.getBlockX();
            y = location.getBlockY() - 1;
            z = location.getBlockZ();
        }
        World world = player.getWorld();
        if (y < world.getMinHeight() || y >= world.getMaxHeight()) {
            sender.sendMessage(Lang.get(Lang.Key.INVALID_POSITION, sender));
            return 0;
        }
        selections.setAndReport(player, number, new Point(world.getUID(), x, y, z));
        return Command.SINGLE_SUCCESS;
    }

    // ---------- Animations ----------

    private int create(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        Player player = requirePlayer(ctx);
        if (player == null) {
            return 0;
        }
        String name = nameArg(ctx);
        Selection selection = selections.get(player.getUniqueId());
        SelectionStatus status = selection.status();
        if (status != SelectionStatus.COMPLETE) {
            sender.sendMessage(Lang.get(selectionError(status), sender));
            return 0;
        }
        Region region = selection.toRegion();
        Point first = selection.pos1();
        World world = first == null ? null : plugin.getServer().getWorld(first.worldId());
        if (world == null) {
            sender.sendMessage(Lang.get(Lang.Key.WORLD_UNAVAILABLE, sender));
            return 0;
        }
        CreateResult result = animations.create(name, world, region);
        TagResolver[] tags = {
                text("name", name),
                number("volume", region.volume()),
                number("limit", result == CreateResult.INVALID_NAME
                        ? Limits.MAX_NAME_LENGTH : settings.get().maxVolumeBlocks())
        };
        Lang.Key key = switch (result) {
            case SUCCESS -> Lang.Key.CREATE_SUCCESS;
            case INVALID_NAME -> Lang.Key.CREATE_INVALID_NAME;
            case EXISTS -> Lang.Key.CREATE_EXISTS;
            case TOO_LARGE -> Lang.Key.CREATE_TOO_LARGE;
        };
        sender.sendMessage(Lang.get(key, sender, tags));
        return result == CreateResult.SUCCESS ? Command.SINGLE_SUCCESS : 0;
    }

    private int delete(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        String name = nameArg(ctx);
        boolean deleted = animations.delete(name);
        sender.sendMessage(Lang.get(deleted ? Lang.Key.DELETED : Lang.Key.NOT_FOUND, sender, text("name", name)));
        return deleted ? Command.SINGLE_SUCCESS : 0;
    }

    private int list(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (animations.getCount() == 0) {
            sender.sendMessage(Lang.get(Lang.Key.LIST_EMPTY, sender));
            return Command.SINGLE_SUCCESS;
        }
        sender.sendMessage(Lang.get(Lang.Key.LIST_HEADER, sender, number("count", animations.getCount())));
        for (String name : animations.names()) {
            Animation animation = animations.find(name);
            if (animation == null) {
                continue;
            }
            sender.sendMessage(Lang.get(Lang.Key.LIST_ENTRY, sender,
                    text("name", animation.getName()),
                    number("frames", animation.getFrameCount()),
                    number("volume", animation.getRegion().volume()),
                    stateTag(animation, sender),
                    queueSuffixTag(animation, sender)));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int info(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        String name = nameArg(ctx);
        Animation animation = animations.find(name);
        if (animation == null) {
            sender.sendMessage(Lang.get(Lang.Key.NOT_FOUND, sender, text("name", name)));
            return 0;
        }
        World world = plugin.getServer().getWorld(animation.getWorldId());
        Region region = animation.getRegion();
        sender.sendMessage(Lang.get(Lang.Key.INFO_HEADER, sender, text("name", animation.getName())));
        sender.sendMessage(Lang.get(Lang.Key.INFO_WORLD, sender,
                text("world", world != null ? world.getName() : animation.getWorldName())));
        sender.sendMessage(Lang.get(Lang.Key.INFO_REGION, sender,
                text("min", region.minX() + ", " + region.minY() + ", " + region.minZ()),
                text("max", region.maxX() + ", " + region.maxY() + ", " + region.maxZ()),
                text("size", region.sizeX() + " x " + region.sizeY() + " x " + region.sizeZ()),
                number("volume", region.volume())));
        sender.sendMessage(Lang.get(Lang.Key.INFO_FRAMES, sender, number("count", animation.getFrameCount())));
        sender.sendMessage(Lang.get(Lang.Key.INFO_STATE, sender, stateTag(animation, sender)));
        int queued = animations.getQueuedCount(animation);
        if (queued > 0) {
            sender.sendMessage(Lang.get(Lang.Key.INFO_QUEUED, sender, number("count", queued)));
        }
        return Command.SINGLE_SUCCESS;
    }

    // ---------- Moving and copying ----------

    private int move(CommandContext<CommandSourceStack> ctx, MoveMode mode, AirMode air) {
        CommandSender sender = ctx.getSource().getSender();
        String name = nameArg(ctx);
        int dx = IntegerArgumentType.getInteger(ctx, ARG_DX);
        int dy = IntegerArgumentType.getInteger(ctx, ARG_DY);
        int dz = IntegerArgumentType.getInteger(ctx, ARG_DZ);
        MoveResult result = animations.move(name, dx, dy, dz, mode, air,
                done -> reportMove(sender, name, dx, dy, dz, done));
        reportMove(sender, name, dx, dy, dz, result);
        return result == MoveResult.STARTED || result == MoveResult.MOVED || result == MoveResult.QUEUED
                ? Command.SINGLE_SUCCESS : 0;
    }

    /** Reports a move outcome. Called once right away and, for block moves, once more when done. */
    private void reportMove(CommandSender sender, String name, int dx, int dy, int dz, MoveResult result) {
        if (sender instanceof Player player && !player.isOnline()) {
            return;
        }
        Lang.Key key = switch (result) {
            case STARTED -> Lang.Key.MOVE_STARTED;
            case MOVED -> Lang.Key.MOVE_REGION_DONE;
            case COMPLETED -> Lang.Key.MOVE_BLOCKS_DONE;
            case NOT_FOUND -> Lang.Key.NOT_FOUND;
            case NO_CHANGE -> Lang.Key.MOVE_NO_CHANGE;
            case BUSY -> Lang.Key.BUSY;
            case WORLD_UNAVAILABLE -> Lang.Key.WORLD_UNAVAILABLE;
            case INVALID_POSITION -> Lang.Key.REGION_OUT_OF_WORLD;
            case OVERLAP -> Lang.Key.MOVE_OVERLAP;
            case CHUNKS_NOT_LOADED -> Lang.Key.CHUNKS_NOT_LOADED;
            case TOO_MANY_STATES -> Lang.Key.TOO_MANY_STATES;
            case QUEUED -> Lang.Key.QUEUED;
            case QUEUE_FULL -> Lang.Key.QUEUE_FULL;
        };
        sender.sendMessage(Lang.get(key, sender,
                text("name", name), number("dx", dx), number("dy", dy), number("dz", dz), queueTags()));
    }

    /**
     * Copies an animation to a new place.
     *
     * @param explicitPosition true if the destination is the {@code position} argument, false for position 1
     * @param show             true if the {@code index} argument names a frame to show on the copy
     */
    private int copy(CommandContext<CommandSourceStack> ctx, boolean explicitPosition, boolean show) {
        CommandSender sender = ctx.getSource().getSender();
        Player player = requirePlayer(ctx);
        if (player == null) {
            return 0;
        }
        String sourceName = nameArg(ctx);
        String name = Animation.normalizeName(StringArgumentType.getString(ctx, ARG_NEW_NAME));
        int frame = show ? indexArg(ctx) : NO_FRAME;
        Animation source = animations.find(sourceName);
        if (source == null) {
            sender.sendMessage(Lang.get(Lang.Key.NOT_FOUND, sender, text("name", sourceName)));
            return 0;
        }
        if (show && frame >= source.getFrameCount()) {
            reportPlay(sender, PlayResult.INVALID_INDEX, Lang.Key.SHOW_STARTED, sourceName,
                    frame + FIRST_FRAME_NUMBER);
            return 0;
        }
        Destination target = explicitPosition ? explicitDestination(ctx, player) : selectionDestination(player);
        if (target == null) {
            return 0;
        }
        CopyResult result = animations.copy(sourceName, name, target.world(), target.x(), target.y(), target.z());
        sender.sendMessage(copyMessage(result, sender, sourceName, name, source, target));
        if (result != CopyResult.SUCCESS) {
            return 0;
        }
        if (show) {
            PlayResult shown = animations.showFrame(name, frame,
                    done -> reportPlay(sender, done, Lang.Key.SHOW_STARTED, name, frame + FIRST_FRAME_NUMBER));
            reportPlay(sender, shown, Lang.Key.SHOW_STARTED, name, frame + FIRST_FRAME_NUMBER);
        }
        return Command.SINGLE_SUCCESS;
    }

    private Component copyMessage(CopyResult result, CommandSender sender, String sourceName, String name,
                                  Animation source, Destination target) {
        Lang.Key key = switch (result) {
            case SUCCESS -> Lang.Key.COPY_SUCCESS;
            case NOT_FOUND -> Lang.Key.NOT_FOUND;
            case INVALID_NAME -> Lang.Key.CREATE_INVALID_NAME;
            case EXISTS -> Lang.Key.CREATE_EXISTS;
            case INVALID_POSITION -> Lang.Key.REGION_OUT_OF_WORLD;
        };
        return Lang.get(key, sender,
                text("source", sourceName),
                text("name", result == CopyResult.NOT_FOUND ? sourceName : name),
                number("frames", source.getFrameCount()),
                number("x", target.x()),
                number("y", target.y()),
                number("z", target.z()),
                number("limit", Limits.MAX_NAME_LENGTH));
    }

    /** Resolves the {@code position} argument in the world of the player; reports and returns null on error. */
    @Nullable
    private static Destination explicitDestination(CommandContext<CommandSourceStack> ctx, Player player) {
        try {
            BlockPosition position = ctx.getArgument(ARG_POSITION, BlockPositionResolver.class)
                    .resolve(ctx.getSource());
            return new Destination(player.getWorld(), position.blockX(), position.blockY(), position.blockZ());
        } catch (CommandSyntaxException ex) {
            player.sendMessage(Lang.get(Lang.Key.INVALID_POSITION, player));
            return null;
        }
    }

    /** Uses position 1 of the player's selection; reports and returns null if it is missing. */
    @Nullable
    private Destination selectionDestination(Player player) {
        Point point = selections.get(player.getUniqueId()).pos1();
        if (point == null) {
            player.sendMessage(Lang.get(Lang.Key.SELECTION_NO_POS1, player));
            return null;
        }
        World world = plugin.getServer().getWorld(point.worldId());
        if (world == null) {
            player.sendMessage(Lang.get(Lang.Key.WORLD_UNAVAILABLE, player));
            return null;
        }
        return new Destination(world, point.x(), point.y(), point.z());
    }

    // ---------- Frames ----------

    private int frameAdd(CommandContext<CommandSourceStack> ctx, int index) {
        CommandSender sender = ctx.getSource().getSender();
        String name = nameArg(ctx);
        FrameResult result = animations.addFrame(name, index,
                done -> reportFrame(sender, name, index, true, done));
        reportFrame(sender, name, index, true, result);
        return isFrameSuccess(result) ? Command.SINGLE_SUCCESS : 0;
    }

    private int frameReplace(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        String name = nameArg(ctx);
        int index = indexArg(ctx);
        FrameResult result = animations.replaceFrame(name, index,
                done -> reportFrame(sender, name, index, false, done));
        reportFrame(sender, name, index, false, result);
        return isFrameSuccess(result) ? Command.SINGLE_SUCCESS : 0;
    }

    private int frameRemove(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        String name = nameArg(ctx);
        int index = indexArg(ctx);
        FrameResult result = animations.removeFrame(name, index);
        reportFrame(sender, name, index, false, result);
        return isFrameSuccess(result) ? Command.SINGLE_SUCCESS : 0;
    }

    private int frameShow(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        String name = nameArg(ctx);
        int index = indexArg(ctx);
        PlayResult result = animations.showFrame(name, index,
                done -> reportPlay(sender, done, Lang.Key.SHOW_STARTED, name, index + 1));
        reportPlay(sender, result, Lang.Key.SHOW_STARTED, name, index + 1);
        return isPlaySuccess(result) ? Command.SINGLE_SUCCESS : 0;
    }

    /**
     * Reports a frame operation outcome. Called once right away and, for captures, once more when done.
     *
     * @param adding true for "add", which accepts one more index than there are frames
     */
    private void reportFrame(CommandSender sender, String name, int index, boolean adding, FrameResult result) {
        if (sender instanceof Player player && !player.isOnline()) {
            return;
        }
        Animation animation = animations.find(name);
        int count = animation == null ? 0 : animation.getFrameCount();
        int shown = index == AnimationManager.APPEND ? count : index + 1;
        TagResolver[] tags = {
                text("name", name),
                number("index", shown),
                number("count", count),
                number("min", FIRST_FRAME_NUMBER),
                number("max", adding ? count + 1 : count),
                number("limit", settings.get().maxFrames()),
                queueTags()
        };
        Lang.Key key = switch (result) {
            case STARTED -> Lang.Key.FRAME_CAPTURING;
            case ADDED -> Lang.Key.FRAME_ADDED;
            case REPLACED -> Lang.Key.FRAME_REPLACED;
            case REMOVED -> Lang.Key.FRAME_REMOVED;
            case NOT_FOUND -> Lang.Key.NOT_FOUND;
            case BUSY -> Lang.Key.BUSY;
            case INVALID_INDEX -> Lang.Key.FRAME_INVALID_INDEX;
            case FRAME_LIMIT -> Lang.Key.FRAME_LIMIT;
            case WORLD_UNAVAILABLE -> Lang.Key.WORLD_UNAVAILABLE;
            case CHUNKS_NOT_LOADED -> Lang.Key.CHUNKS_NOT_LOADED;
            case TOO_MANY_STATES -> Lang.Key.TOO_MANY_STATES;
            case QUEUED -> Lang.Key.QUEUED;
            case QUEUE_FULL -> Lang.Key.QUEUE_FULL;
        };
        sender.sendMessage(Lang.get(key, sender, tags));
    }

    private static boolean isFrameSuccess(FrameResult result) {
        return result == FrameResult.STARTED || result == FrameResult.ADDED
                || result == FrameResult.REPLACED || result == FrameResult.REMOVED
                || result == FrameResult.QUEUED;
    }

    // ---------- Playback ----------

    private int play(CommandContext<CommandSourceStack> ctx, PlayDirection direction, PlayMode mode) {
        CommandSender sender = ctx.getSource().getSender();
        String name = nameArg(ctx);
        int delayTicks = Ticks.fromSeconds(DoubleArgumentType.getDouble(ctx, ARG_DELAY));
        TagResolver[] extra = {
                text("direction", direction.getId()),
                text("mode", mode.getId()),
                text("delay", Ticks.toSecondsString(delayTicks))
        };
        PlayResult result = animations.play(name, direction, mode, delayTicks,
                done -> reportPlay(sender, done, Lang.Key.PLAY_STARTED, name, FIRST_FRAME_NUMBER, extra));
        reportPlay(sender, result, Lang.Key.PLAY_STARTED, name, FIRST_FRAME_NUMBER, extra);
        return isPlaySuccess(result) ? Command.SINGLE_SUCCESS : 0;
    }

    private int stop(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        String name = nameArg(ctx);
        StopResult result = animations.stop(name);
        Lang.Key key = switch (result) {
            case STOPPED -> Lang.Key.STOPPED;
            case NOT_FOUND -> Lang.Key.NOT_FOUND;
            case NOT_RUNNING -> Lang.Key.NOT_RUNNING;
            case QUEUE_CLEARED -> Lang.Key.QUEUE_CLEARED;
        };
        sender.sendMessage(Lang.get(key, sender, text("name", name)));
        return result == StopResult.STOPPED || result == StopResult.QUEUE_CLEARED ? Command.SINGLE_SUCCESS : 0;
    }

    private int stopAll(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        int queued = animations.getQueueSize();
        int stopped = animations.stopAll();
        if (stopped == 0 && queued == 0) {
            sender.sendMessage(Lang.get(Lang.Key.NOTHING_RUNNING, sender));
            return 0;
        }
        if (stopped > 0) {
            sender.sendMessage(Lang.get(Lang.Key.STOPPED_ALL, sender, number("count", stopped)));
        }
        if (queued > 0) {
            sender.sendMessage(Lang.get(Lang.Key.QUEUE_CLEARED_ALL, sender, number("queued", queued)));
        }
        return stopped + queued;
    }

    private Component playMessage(PlayResult result, Lang.Key started, CommandSender sender, String name,
                                  int shownIndex, TagResolver... extra) {
        TagResolver tags = TagResolver.builder()
                .resolvers(
                        text("name", name),
                        number("index", shownIndex),
                        number("min", FIRST_FRAME_NUMBER),
                        number("max", frameCountOf(name)),
                        number("limit", settings.get().maxRunningAnimations()),
                        queueTags())
                .resolvers(extra)
                .build();
        Lang.Key key = switch (result) {
            case STARTED -> started;
            case NOT_FOUND -> Lang.Key.NOT_FOUND;
            case BUSY -> Lang.Key.BUSY;
            case ALREADY_RUNNING -> Lang.Key.PLAY_ALREADY_RUNNING;
            case NO_FRAMES -> Lang.Key.PLAY_NO_FRAMES;
            case NOT_ENOUGH_FRAMES -> Lang.Key.PLAY_NOT_ENOUGH_FRAMES;
            case INVALID_INDEX -> Lang.Key.FRAME_INVALID_INDEX;
            case WORLD_UNAVAILABLE -> Lang.Key.WORLD_UNAVAILABLE;
            case RUNNING_LIMIT -> Lang.Key.PLAY_RUNNING_LIMIT;
            case OVERLAP -> Lang.Key.PLAY_OVERLAP;
            case QUEUED -> Lang.Key.QUEUED;
            case QUEUE_FULL -> Lang.Key.QUEUE_FULL;
        };
        return Lang.get(key, sender, tags);
    }

    /** Reports a playback outcome. Called once right away and, for queued operations, once more when decided. */
    private void reportPlay(CommandSender sender, PlayResult result, Lang.Key started, String name, int shownIndex,
                            TagResolver... extra) {
        if (sender instanceof Player player && !player.isOnline()) {
            return;
        }
        sender.sendMessage(playMessage(result, started, sender, name, shownIndex, extra));
    }

    private static boolean isPlaySuccess(PlayResult result) {
        return result == PlayResult.STARTED || result == PlayResult.QUEUED;
    }

    private int frameCountOf(String name) {
        Animation animation = animations.find(name);
        return animation == null ? 0 : animation.getFrameCount();
    }

    private int reload(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        plugin.reloadAll();
        sender.sendMessage(Lang.get(Lang.Key.RELOADED, sender));
        return Command.SINGLE_SUCCESS;
    }

    // ---------- Suggestions ----------

    private CompletableFuture<Suggestions> suggestNames(CommandContext<CommandSourceStack> ctx,
                                                        SuggestionsBuilder builder) {
        String prefix = builder.getRemainingLowerCase();
        for (String name : animations.names()) {
            if (name.startsWith(prefix)) {
                builder.suggest(name);
            }
        }
        return builder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestIndices(CommandContext<CommandSourceStack> ctx,
                                                          SuggestionsBuilder builder, boolean allowNext) {
        Animation animation = animations.find(nameArg(ctx));
        if (animation != null) {
            int max = Math.min(animation.getFrameCount() + (allowNext ? 1 : 0), MAX_INDEX_SUGGESTIONS);
            String prefix = builder.getRemaining();
            for (int number = FIRST_FRAME_NUMBER; number <= max; number++) {
                if (String.valueOf(number).startsWith(prefix)) {
                    builder.suggest(number);
                }
            }
        }
        return builder.buildFuture();
    }

    // ---------- Helpers ----------

    private static Predicate<CommandSourceStack> has(String permission) {
        return src -> src.getSender().hasPermission(permission);
    }

    /** Returns the executing player, or tells the sender that only players can do this and returns null. */
    @Nullable
    private static Player requirePlayer(CommandContext<CommandSourceStack> ctx) {
        if (ctx.getSource().getExecutor() instanceof Player player) {
            return player;
        }
        CommandSender sender = ctx.getSource().getSender();
        sender.sendMessage(Lang.get(Lang.Key.ONLY_PLAYERS, sender));
        return null;
    }

    private static String nameArg(CommandContext<CommandSourceStack> ctx) {
        return Animation.normalizeName(StringArgumentType.getString(ctx, ARG_NAME));
    }

    /** Returns the 0-based frame index of the 1-based command argument. */
    private static int indexArg(CommandContext<CommandSourceStack> ctx) {
        return IntegerArgumentType.getInteger(ctx, ARG_INDEX) - FIRST_FRAME_NUMBER;
    }

    private static Lang.Key selectionError(SelectionStatus status) {
        return switch (status) {
            case NO_POS1 -> Lang.Key.SELECTION_NO_POS1;
            case NO_POS2 -> Lang.Key.SELECTION_NO_POS2;
            case DIFFERENT_WORLDS -> Lang.Key.SELECTION_DIFFERENT_WORLDS;
            case COMPLETE -> throw new IllegalArgumentException("The selection is complete");
        };
    }

    private TagResolver stateTag(Animation animation, CommandSender sender) {
        Lang.Key key = switch (animations.getState(animation)) {
            case IDLE -> Lang.Key.STATE_IDLE;
            case PLAYING -> Lang.Key.STATE_PLAYING;
            case CAPTURING -> Lang.Key.STATE_CAPTURING;
            case MOVING -> Lang.Key.STATE_MOVING;
        };
        return Placeholder.component("state", Lang.get(key, sender));
    }

    /** Empty when nothing of the animation is queued, otherwise the number of queued operations. */
    private TagResolver queueSuffixTag(Animation animation, CommandSender sender) {
        int queued = animations.getQueuedCount(animation);
        Component suffix = queued == 0
                ? Component.empty() : Lang.get(Lang.Key.LIST_QUEUED, sender, number("count", queued));
        return Placeholder.component("queue", suffix);
    }

    /** Tags of the queue messages: operations waiting now and the configured limit. */
    private TagResolver queueTags() {
        return TagResolver.builder()
                .resolver(number("queued", animations.getQueueSize()))
                .resolver(number("queue-limit", settings.get().maxQueuedOperations()))
                .build();
    }

    private static TagResolver text(String key, String value) {
        return Placeholder.unparsed(key, value);
    }

    private static TagResolver number(String key, long value) {
        return Placeholder.unparsed(key, String.valueOf(value));
    }
}
