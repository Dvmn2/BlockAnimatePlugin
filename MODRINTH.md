# BlockAnimatePlugin

Paper plugin that saves several "frames" of a block region and plays them as an animation, forward or backward, with a delay between frames.

## Features
- Select a region with a wand or with commands and save it under a name
- Capture any number of frames of the region
- Play forward or backward with a delay in seconds, once, in a loop or ping-pong
- Show a single frame, replace or remove frames, stop animations at any time
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
| `/blockanimate frame add <name> [index]` | `blockanimate.edit` | Capture the region as a new frame (appended, or inserted at `index`) |
| `/blockanimate frame replace <name> <index>` | `blockanimate.edit` | Replace a frame with the current state of the region |
| `/blockanimate frame remove <name> <index>` | `blockanimate.edit` | Remove a frame |
| `/blockanimate frame show <name> <index>` | `blockanimate.play` | Show one frame |
| `/blockanimate play <name> forward <delay> [once\|loop\|pingpong]` | `blockanimate.play` | Play from the first frame |
| `/blockanimate play <name> backward <delay> [once\|loop\|pingpong]` | `blockanimate.play` | Play from the last frame |
| `/blockanimate stop [name]` | `blockanimate.play` | Stop one animation, or all of them |
| `/blockanimate reload` | `blockanimate.reload` | Reload `config.yml` |

All commands also need `blockanimate.use`.

Playback modes: `once` (default) plays to the end and stops on the last shown frame; `loop` jumps back to the start; `pingpong` reverses at both ends. `loop` and `pingpong` need at least 2 frames.

## Permissions
| Permission | Default | Description |
|---|---|---|
| `blockanimate.use` | op | Use `/blockanimate`, list and inspect animations |
| `blockanimate.select` | op | Use the wand and `pos1`, `pos2` |
| `blockanimate.edit` | op | Create and delete animations, add, replace and remove frames |
| `blockanimate.play` | op | Play and stop animations, show frames |
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
- **An animation file is skipped at start**: check the server log for the reason; the file is left untouched.

## License
All rights reserved.
