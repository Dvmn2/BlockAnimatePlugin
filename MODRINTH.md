# BlockAnimatePlugin

Paper plugin that saves several "frames" of a block region and plays them as an animation, forward or backward, with a delay between frames.

## Features
- Select a region with a wand or with commands and save it under a name
- Capture any number of frames of the region
- Play forward or backward with a delay in seconds, once, in a loop or ping-pong
- Show a single frame, replace or remove frames, stop animations at any time
- Move an animation region, with or without its blocks (the old blocks can be kept or removed)
- Copy an animation to another place, optionally showing a frame there right away
- Large frames are applied over several ticks to keep the server responsive
- Animations survive restarts; messages in Russian and English

## Requirements
- Paper 1.21.11, Java 21
- No other plugins

## Installation
1. Put `BlockAnimatePlugin-<version>.jar` into the `plugins` folder.
2. Start the server; `plugins/BlockAnimatePlugin/config.yml` is created.

Quick start:
1. `/blockanimate wand`, then left click a block (position 1) and right click a block (position 2).
2. `/blockanimate create door`
3. Build the first state and run `/blockanimate frame add door`; change the build and add the next frame.
4. `/blockanimate play door forward 0.5` (use `loop` or `pingpong` at the end to repeat).
5. `/blockanimate copy door door2 ~10 ~ ~ show 1` makes a second animation 10 blocks away and shows its first frame.

## Commands
Frame numbers start at 1. Delays are in seconds, fractions allowed, minimum `0.05` (one tick), maximum `3600`.

| Command | Permission | Description |
|---|---|---|
| `/blockanimate wand` | `blockanimate.select` | Give the selection wand |
| `/blockanimate pos1 [position]` | `blockanimate.select` | Set position 1 (default: the block under you) |
| `/blockanimate pos2 [position]` | `blockanimate.select` | Set position 2 (default: the block under you) |
| `/blockanimate create <name>` | `blockanimate.edit` | Save the selected region as an animation |
| `/blockanimate delete <name>` | `blockanimate.edit` | Delete an animation |
| `/blockanimate list` | `blockanimate.use` | List animations |
| `/blockanimate info <name>` | `blockanimate.use` | Show region, world, frame count and state |
| `/blockanimate move <name> <dx> <dy> <dz>` | `blockanimate.edit` | Move the region by an offset in blocks; blocks in the world are not touched |
| `/blockanimate move <name> <dx> <dy> <dz> blocks keep` | `blockanimate.edit`, `blockanimate.play` | Move the region and copy its blocks; the old blocks stay |
| `/blockanimate move <name> <dx> <dy> <dz> blocks remove` | `blockanimate.edit`, `blockanimate.play` | Move the region and its blocks; the old blocks become air |
| `/blockanimate copy <name> <new-name> [position]` | `blockanimate.edit` | Create a new animation with the same frames; the minimum corner of its region is placed at `position` (or at position 1) |
| `/blockanimate copy <name> <new-name> [position] show <index>` | `blockanimate.edit`, `blockanimate.play` | Same, then show the given frame at the new place |
| `/blockanimate frame add <name> [index]` | `blockanimate.edit` | Capture the region as a new frame (appended, or inserted at `index`) |
| `/blockanimate frame replace <name> <index>` | `blockanimate.edit` | Replace a frame with the current state of the region |
| `/blockanimate frame remove <name> <index>` | `blockanimate.edit` | Remove a frame |
| `/blockanimate frame show <name> <index>` | `blockanimate.play` | Show one frame |
| `/blockanimate play <name> forward <delay> [once\|loop\|pingpong]` | `blockanimate.play` | Play from the first frame |
| `/blockanimate play <name> backward <delay> [once\|loop\|pingpong]` | `blockanimate.play` | Play from the last frame |
| `/blockanimate stop [name]` | `blockanimate.play` | Stop one animation, or all of them |
| `/blockanimate reload` | `blockanimate.reload` | Reload `config.yml` |

All commands also need `blockanimate.use`.

Moving and copying:
- Offsets are in blocks (`-60000000..60000000`) and the region size never changes. `move` works in the animation's world; `copy` works in the world of the player, or of position 1 when no `position` is given. Without `position`, `copy` needs position 1 to be set.
- `move` without `blocks` only re-points the animation: the old blocks stay in the world and the next frame change rewrites the whole new region.
- With `blocks`, the blocks that are in the world right now (including manual edits) are copied to the new place, then, with `remove`, the old region is cleared except for the part shared with the new one. The work is spread over several ticks and a message is sent when it is done. Everything inside the new region is overwritten.
- A move is refused while the animation is playing, capturing or moving, if the area overlaps a running animation or another move, if a chunk is not loaded, or if the region would leave the world.
- If the server stops in the middle of a block move, the animation stays at the old place and the new place may be partly filled. There is no undo.
- `copy` does not change the world unless `show` is used, and it does not copy blocks of the world, only the saved frames. Frames are shared, so a copy costs almost no extra memory.

Playback modes: `once` (default) plays to the end and stops on the last shown frame; `loop` jumps back to the start; `pingpong` reverses at both ends. `loop` and `pingpong` need at least 2 frames.

## Permissions
| Permission | Default | Description |
|---|---|---|
| `blockanimate.use` | op | Use `/blockanimate`, list and inspect animations |
| `blockanimate.select` | op | Use the wand and `pos1`, `pos2` |
| `blockanimate.edit` | op | Create, delete, move and copy animations, add, replace and remove frames |
| `blockanimate.play` | op | Play and stop animations, show frames, move blocks with `move ... blocks`, use `copy ... show` |
| `blockanimate.reload` | op | Use `/blockanimate reload` |
| `blockanimate.admin` | op | All of the above |

## Configuration
File: `plugins/BlockAnimatePlugin/config.yml`

| Key | Type | Default | Range | Description |
|---|---|---|---|---|
| `settings.language` | string | `auto` | `ru`, `en`, `auto` | Message language; `auto` follows the player's client language, the console uses English |
| `settings.max-volume-blocks` | int | `100000` | `1..1000000` | Maximum blocks in one region |
| `settings.max-frames` | int | `200` | `1..1000` | Maximum frames per animation |
| `settings.max-running-animations` | int | `8` | `1..64` | Animations that can play at the same time |
| `settings.max-blocks-per-tick` | int | `4096` | `64..100000` | Blocks one animation may change per tick |
| `settings.apply-physics` | boolean | `false` | `true`, `false` | Whether placed blocks trigger physics (gravity, fluids, redstone) |
| `settings.autosave-seconds` | int | `30` | `5..3600` | How often changed animations are saved |

Example for a server with big structures:

```yaml
settings:
  language: en
  max-volume-blocks: 250000
  max-blocks-per-tick: 8192
```

Memory use is roughly 2 bytes per block per frame. Lowering a limit does not affect existing animations.

## Data
Animations are stored in `plugins/BlockAnimatePlugin/animations/`, one `<name>.bin` file each. To back up, copy that folder. To delete one animation, use `/blockanimate delete <name>`; to reset everything, stop the server and clear the folder.

## Companion mod
None.

## Troubleshooting
- **"Some chunks of the region are not loaded"**: move closer so the whole region is loaded and repeat the command.
- **Chest contents or sign text are lost after playing**: only block types and states are saved, not block contents.
- **A big frame appears piece by piece**: raise `settings.max-blocks-per-tick`, or use a smaller region.
- **Sand or water moves after a frame change**: keep `settings.apply-physics` set to `false`.
- **"The region overlaps with a running animation"**: stop the other animation first.
- **A move or copy says the region would leave the world**: the new region must fit the height range of the world and be within 30,000,000 blocks in X and Z.
- **Chest contents or sign text are lost after `move ... blocks`**: only block types and states are moved, not block contents.
- **An animation file is skipped at start**: check the server log for the reason; the file is left untouched.

## License
All rights reserved.
