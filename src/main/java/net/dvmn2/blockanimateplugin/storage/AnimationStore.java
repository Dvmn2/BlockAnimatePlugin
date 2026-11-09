package net.dvmn2.blockanimateplugin.storage;

import net.dvmn2.blockanimateplugin.model.Animation;
import net.dvmn2.blockanimateplugin.model.AnimationSnapshot;
import net.dvmn2.blockanimateplugin.model.Frame;
import net.dvmn2.blockanimateplugin.model.Limits;
import net.dvmn2.blockanimateplugin.model.Region;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Reads and writes animation files, one gzip file per animation.
 *
 * <p>{@link #loadAll()} is called on the main thread during start-up. {@link #write} and {@link #delete}
 * may be called from any thread, but never concurrently for the same animation.
 *
 * <pre>
 * Int     magic            0x42414E4D
 * Int     format version   1
 * UTF     name
 * Long    world UUID most significant bits
 * Long    world UUID least significant bits
 * UTF     world name
 * Int x6  minX minY minZ maxX maxY maxZ
 * Int     frame count
 * per frame:
 *   Int     palette size
 *   UTF     palette entries (block state strings)
 *   Short   one unsigned palette index per cell (X fastest, then Z, then Y)
 * </pre>
 */
public final class AnimationStore {

    private static final int MAGIC = 0x42414E4D;
    private static final int FORMAT_VERSION = 1;
    private static final String EXTENSION = ".bin";

    private final Path directory;
    private final Logger log;

    public AnimationStore(Path directory, Logger log) {
        this.directory = directory;
        this.log = log;
    }

    /** Reads every valid animation file. Broken files are reported and skipped. */
    public List<AnimationSnapshot> loadAll() {
        List<AnimationSnapshot> result = new ArrayList<>();
        List<Path> files;
        try {
            Files.createDirectories(directory);
            try (Stream<Path> stream = Files.list(directory)) {
                files = stream
                        .filter(path -> path.getFileName().toString().endsWith(EXTENSION))
                        .sorted()
                        .toList();
            }
        } catch (IOException ex) {
            log.log(Level.SEVERE, "Failed to list animation files in " + directory, ex);
            return result;
        }
        for (Path file : files) {
            String fileName = file.getFileName().toString();
            try {
                AnimationSnapshot snapshot = read(file);
                String expected = fileName.substring(0, fileName.length() - EXTENSION.length());
                if (!snapshot.name().equals(expected)) {
                    log.warning("Skipping animation file " + fileName + ": it contains the name '"
                            + snapshot.name() + "'");
                    continue;
                }
                result.add(snapshot);
            } catch (IOException ex) {
                log.warning("Skipping animation file " + fileName + ": " + ex.getMessage());
            }
        }
        return result;
    }

    /** Writes an animation atomically. */
    public void write(AnimationSnapshot snapshot) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(buffer)))) {
            out.writeInt(MAGIC);
            out.writeInt(FORMAT_VERSION);
            out.writeUTF(snapshot.name());
            out.writeLong(snapshot.worldId().getMostSignificantBits());
            out.writeLong(snapshot.worldId().getLeastSignificantBits());
            out.writeUTF(snapshot.worldName());
            Region region = snapshot.region();
            out.writeInt(region.minX());
            out.writeInt(region.minY());
            out.writeInt(region.minZ());
            out.writeInt(region.maxX());
            out.writeInt(region.maxY());
            out.writeInt(region.maxZ());
            out.writeInt(snapshot.frames().size());
            for (Frame frame : snapshot.frames()) {
                writeFrame(out, frame);
            }
        }
        writeAtomically(directory.resolve(snapshot.name() + EXTENSION), buffer.toByteArray());
    }

    /** Deletes the file of an animation if it exists. */
    public void delete(String name) throws IOException {
        Files.deleteIfExists(directory.resolve(name + EXTENSION));
    }

    private static void writeFrame(DataOutputStream out, Frame frame) throws IOException {
        out.writeInt(frame.palette().size());
        for (String state : frame.palette()) {
            out.writeUTF(state);
        }
        ByteBuffer raw = ByteBuffer.allocate(frame.cellCount() * Short.BYTES);
        raw.asShortBuffer().put(frame.indices());
        out.write(raw.array());
    }

    private static AnimationSnapshot read(Path file) throws IOException {
        try (DataInputStream in = new DataInputStream(
                new BufferedInputStream(new GZIPInputStream(Files.newInputStream(file))))) {
            if (in.readInt() != MAGIC) {
                throw new IOException("not a Block Animate file");
            }
            int version = in.readInt();
            if (version != FORMAT_VERSION) {
                throw new IOException("unsupported format version " + version);
            }
            String name = in.readUTF();
            if (!Animation.NAME_PATTERN.matcher(name).matches()) {
                throw new IOException("invalid animation name");
            }
            UUID worldId = new UUID(in.readLong(), in.readLong());
            String worldName = in.readUTF();
            Region region = new Region(in.readInt(), in.readInt(), in.readInt(),
                    in.readInt(), in.readInt(), in.readInt());
            long volume = region.volume();
            if (volume < 1 || volume > Limits.MAX_VOLUME) {
                throw new IOException("region volume out of range: " + volume);
            }
            int frameCount = in.readInt();
            if (frameCount < 0 || frameCount > Limits.MAX_FRAMES) {
                throw new IOException("frame count out of range: " + frameCount);
            }
            List<Frame> frames = new ArrayList<>(frameCount);
            for (int i = 0; i < frameCount; i++) {
                frames.add(readFrame(in, (int) volume));
            }
            return new AnimationSnapshot(name, worldId, worldName, region, frames);
        }
    }

    private static Frame readFrame(DataInputStream in, int cells) throws IOException {
        int paletteSize = in.readInt();
        if (paletteSize < 1 || paletteSize > Limits.MAX_PALETTE) {
            throw new IOException("palette size out of range: " + paletteSize);
        }
        List<String> palette = new ArrayList<>(paletteSize);
        for (int i = 0; i < paletteSize; i++) {
            palette.add(in.readUTF());
        }
        byte[] raw = new byte[cells * Short.BYTES];
        in.readFully(raw);
        short[] indices = new short[cells];
        ByteBuffer.wrap(raw).asShortBuffer().get(indices);
        for (short index : indices) {
            if ((index & 0xFFFF) >= paletteSize) {
                throw new IOException("palette index out of range");
            }
        }
        return new Frame(palette, indices);
    }

    private static void writeAtomically(Path target, byte[] bytes) throws IOException {
        Files.createDirectories(target.getParent());
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.write(tmp, bytes);
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
