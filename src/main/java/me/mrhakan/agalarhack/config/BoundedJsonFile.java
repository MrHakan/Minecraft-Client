package me.mrhakan.agalarhack.config;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.function.Function;

/** Reads before allowing writes; failed reads preserve the original until a successful reload. */
public final class BoundedJsonFile<T> {
    private final Path path;
    private final int limit;
    private final Function<String, T> decode;
    private final Function<T, String> encode;
    private boolean writable;

    public BoundedJsonFile(Path path, int limit, Function<String, T> decode, Function<T, String> encode) {
        if (limit < 1 || limit > 4 * 1024 * 1024) throw new IllegalArgumentException("Invalid file limit");
        this.path = path.toAbsolutePath();
        this.limit = limit;
        this.decode = decode;
        this.encode = encode;
    }

    public Optional<T> load() throws IOException {
        writable = false;
        if (Files.notExists(path)) {
            writable = true;
            return Optional.empty();
        }
        byte[] bytes;
        try (var input = Files.newInputStream(path)) {
            bytes = input.readNBytes(limit + 1);
        }
        if (bytes.length > limit) throw new IOException("Config exceeds " + limit + " bytes");
        T result;
        try {
            String json = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
            result = decode.apply(json);
            if (result == null) throw new IllegalArgumentException("Empty config");
        } catch (RuntimeException failure) {
            throw new IOException("Invalid config; original file preserved", failure);
        }
        writable = true;
        return Optional.of(result);
    }

    public void save(T value) throws IOException {
        if (!writable) throw new IOException("Saving disabled until config can be read successfully");
        byte[] bytes = encode.apply(value).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > limit) throw new IOException("Config exceeds " + limit + " bytes");
        Files.createDirectories(path.getParent());
        Path temporary = Files.createTempFile(path.getParent(), path.getFileName() + ".", ".tmp");
        try {
            Files.write(temporary, bytes);
            try {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
