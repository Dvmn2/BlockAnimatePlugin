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
| `AnimationManager` | registry of animations, frame/playback facade, dirty tracking, autosave |
| `CaptureManager` | reads a region into a `Frame` in slices; waits for unloaded chunks |
| `PlaybackManager` | owns running sessions, one timer tick advances all of them |
| `PlaybackSession` | state machine of one playing animation (apply, wait, choose next frame) |
| `SelectionManager` | per-player pos1/pos2 selections |
| `WandFactory`, `BlockAnimateListener` | selection wand item and its click handling |
| `command.BlockAnimateCommand` | Brigadier command tree |
| `model.*` | `Animation`, `Frame`, `Region`, `Selection`, result enums, `Limits` |
| `storage.AnimationStore` | binary gzip files, one per animation |
| `util.Ticks`, `util.ChunkGuard` | tick conversion, loaded-chunk check |

Threading: all Bukkit API calls and all model access are on the main thread. Disk writes run on a single-thread executor (`BlockAnimate-IO`) from immutable `AnimationSnapshot`s; a `Frame` stores block states as strings, so no Bukkit object crosses threads.

Lifecycle:
1. `onEnable`: config and language, factories, managers, `AnimationManager.load()`, listener, command registration, `AnimationManager.start()` (capture timer, playback timer, autosave timer).
2. `onDisable`: cancel timers, drop sessions and captures, wait for pending writes (10 s), write remaining dirty animations synchronously.

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
- Frame operations (`add`, `replace`, `remove`) are rejected while an animation is playing or capturing, so the frame list is stable during a session.
- A `play` or `frame show` is rejected if another running animation overlaps the region. Overlapping animations may exist while idle.
- `loop` and `pingpong` need at least two frames. `pingpong` does not repeat the end frames.
- Running animations are not restored after a restart; animation data is.
- `pos1` and `pos2` without coordinates use the block under the player.
- The animation name is lower-cased and must match `[a-z0-9_-]{1,32}`; it is also the file name.

## License
All rights reserved.
