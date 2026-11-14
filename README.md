# BlockAnimatePlugin

Paper plugin that saves several "frames" of a block region and plays them as an animation, forward or backward, with a delay between frames. Companion client mod: none.

## Requirements
- Minecraft 1.21.11, Paper, Java 21
- No other plugins or mods

## Build and run
```bash
gradle wrapper       # once: the Gradle wrapper files are not included in the archive
./gradlew build      # build/libs/BlockAnimatePlugin-<version>.jar
./gradlew runServer  # Paper 1.21.11 test server
```

## Architecture
| Class | Responsibility |
|---|---|
| `BlockAnimatePlugin` | composition root: wiring, enable/disable order, config reload |
| `BlockAnimateSettings` | immutable snapshot of `config.yml` with range checks |
| `Lang` | RU/EN texts (MiniMessage), the only static mutable state is the language |
| `Permissions`, `PluginKeys` | permission node constants, persistent data keys |
| `BlockDataFactory` | cached `BlockData` creation from block state strings |
| `AnimationManager` | registry of animations, frame/playback/move/copy facade, dirty tracking, autosave |
| `CaptureManager` | reads a region into a `Frame` in slices; waits for unloaded chunks |
| `RelocationManager` | moves the blocks of a region in slices: reads them through `CaptureManager`, writes the new region, then clears the old one |
| `PlaybackManager` | owns running sessions, one timer tick advances all of them |
| `PlaybackSession` | state machine of one playing animation (apply, wait, choose next frame) |
| `SelectionManager` | per-player pos1/pos2 selections |
| `WandFactory`, `BlockAnimateListener` | selection wand item and its click handling |
| `command.BlockAnimateCommand` | Brigadier command tree |
| `model.*` | `Animation`, `Frame`, `Region`, `Selection`, result enums, `MoveMode`, `Limits` |
| `storage.AnimationStore` | binary gzip files, one per animation |
| `util.Ticks`, `util.ChunkGuard` | tick conversion, loaded-chunk check |

Threading: all Bukkit API calls and all model access are on the main thread. Disk writes run on a single-thread executor (`BlockAnimate-IO`) from immutable `AnimationSnapshot`s; a `Frame` stores block states as strings, so no Bukkit object crosses threads.

Lifecycle:
1. `onEnable`: config and language, factories, managers, `AnimationManager.load()`, listener, command registration, `AnimationManager.start()` (capture timer, playback timer, relocation timer, autosave timer).
2. `onDisable`: cancel timers, drop sessions, captures and moves, wait for pending writes (10 s), write remaining dirty animations synchronously.

## Public API
None.

## Protocol
None.

## Data and files
`plugins/BlockAnimatePlugin/animations/<name>.bin`, one gzip-compressed file per animation. Big-endian layout, version 1:

```text
Int     magic            0x42414E4D
Int     format version   1
UTF     name
Long x2 world UUID (most, least significant bits)
UTF     world name
Int x6  minX minY minZ maxX maxY maxZ
Int     frame count
per frame:
  Int   palette size
  UTF   palette entries (block state strings)
  Short one unsigned palette index per cell (X fastest, then Z, then Y)
```

Writes are atomic (temporary file plus move). Unknown versions and corrupted files are skipped with a warning. Block states unknown to the running game version are replaced by air at load time and reported once per state. A new format version MUST bump `FORMAT_VERSION` and keep a reader for version 1.

## Conventions and constraints
- Frames store only `BlockData`. Block entity contents (chest items, sign text, banner patterns, heads) are not saved, and overwriting such a block loses its contents.
- Playing from a known frame changes only blocks that differ between the two frames. The plugin tracks which frame is displayed (`Animation.displayedFrame`); manual edits to blocks that are equal in both frames are therefore not overwritten. After a restart, a failed step or a stop in the middle of a frame, the state is unknown and the next step rewrites every block.
- Frame changes are budgeted by `settings.max-blocks-per-tick` per animation. Small diffs appear within one tick; larger ones are spread over several ticks, and the delay is counted from the end of the last tick of a frame.
- Capturing reads `settings.max-blocks-per-tick * 4` blocks per tick.
- Chunks are never force-loaded. A step or capture waits while a chunk of the region is unloaded. A session ends if its world is unloaded.
- Frame operations (`add`, `replace`, `remove`), `move` and `play` are rejected while an animation is playing, capturing or moving, so the frame list and the region are stable during a session.
- A `play` or `frame show` is rejected if another running animation overlaps the region. Overlapping animations may exist while idle.
- `loop` and `pingpong` need at least two frames. `pingpong` does not repeat the end frames.
- Running animations are not restored after a restart; animation data is.
- `move` without `blocks` only changes `Animation.region` and forgets the displayed frame. With `blocks`, the blocks that are in the world right now are captured (so manual edits move too), pasted into the new region and, for `remove`, the cells of the old region outside the new region are replaced with air. Pasting comes before clearing, and the new region is committed only when everything is done, so an interrupted move never loses blocks. A move that fails or is dropped leaves the old region and forgets the displayed frame. A successful block move keeps the displayed frame.
- With `skip-air` the air cells of the captured blocks are not written, so what stands at the new place stays; `air` (the default) writes them. `skip-air` is not blocked when the old and the new region overlap: together with `remove`, an old block that stood where the moved blocks have air then remains, because that cell belongs to the new region and is not cleared. Air means `Material#isAir` (air, cave air, void air).
- A block move is rejected if the new region (and, for `remove`, the old one) overlaps a running animation or another move, or if a chunk of either region is unloaded at the start. Later unloads make it wait. Shutdown drops it and leaves a partially written destination; there is no undo.
- The shifted or copied region MUST lie inside the world height range and within 30,000,000 blocks of the origin in X and Z. Offsets of `move` are limited to 60,000,000 so coordinates never overflow `int`.
- `copy` shares the `Frame` objects of the source with the new animation. This is safe because frames are immutable; `replace` swaps a reference in one animation only. The copy can be made in any state of the source and does not change the world. Its optional `show` part uses the normal frame display, so the usual playback rules apply.
- `copy` puts the minimum corner of the new region at `position`, in the world of the player, or at position 1 of the selection (in the world of that point) when no position is given.
- `pos1` and `pos2` without coordinates use the block under the player.
- The animation name is lower-cased and must match `[a-z0-9_-]{1,32}`; it is also the file name.

## License
All rights reserved.
