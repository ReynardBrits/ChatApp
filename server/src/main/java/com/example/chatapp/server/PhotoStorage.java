package com.example.chatapp.server;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Locale;
import java.util.UUID;

public final class PhotoStorage {

    public static final int MAX_BYTES = 5 * 1024 * 1024;

    private static final long MAX_PIXELS = 20_000_000;
    private static final int MAX_DIMENSION = 10_000;

    private final Path directory;

    public record StoredPhoto(
            String name,
            String contentType,
            long size
    ) {}

    public PhotoStorage(Path directory) throws IOException {
        this.directory = directory.toAbsolutePath().normalize();
        Files.createDirectories(this.directory);
    }

    public StoredPhoto store(InputStream input) throws IOException {
        byte[] bytes = input.readNBytes(MAX_BYTES + 1);

        if (bytes.length == 0) {
            throw new IllegalArgumentException("Choose a photo.");
        }

        if (bytes.length > MAX_BYTES) {
            throw new IllegalArgumentException(
                    "Photos cannot exceed 5 MB."
            );
        }

        String extension = validateImage(bytes);
        String name = UUID.randomUUID() + "." + extension;

        Path destination = resolve(name);
        Path temporary = Files.createTempFile(
                directory,
                "upload-",
                ".tmp"
        );

        try {
            Files.write(temporary, bytes);
            Files.move(temporary, destination);

        } finally {
            Files.deleteIfExists(temporary);
        }

        String contentType = extension.equals("jpg")
                ? "image/jpeg"
                : "image/png";

        return new StoredPhoto(name, contentType, bytes.length);
    }

    public Path resolve(String name) {
        if (name == null || !name.matches(
                "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-"
                        + "[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png)"
        )) {
            throw new IllegalArgumentException(
                    "Invalid photo filename."
            );
        }

        return directory.resolve(name);
    }

    public void delete(String name) throws IOException {
        Files.deleteIfExists(resolve(name));
    }

    private String validateImage(byte[] bytes) {
        try (MemoryCacheImageInputStream stream =
                     new MemoryCacheImageInputStream(
                             new ByteArrayInputStream(bytes)
                     )) {

            Iterator<ImageReader> readers =
                    ImageIO.getImageReaders(stream);

            if (!readers.hasNext()) {
                throw new IllegalArgumentException(
                        "Choose a valid JPEG or PNG photo."
                );
            }

            ImageReader reader = readers.next();

            try {
                reader.setInput(stream, true, true);

                String format = reader.getFormatName()
                        .toLowerCase(Locale.ROOT);

                String extension = switch (format) {
                    case "jpeg", "jpg" -> "jpg";
                    case "png" -> "png";
                    default -> throw new IllegalArgumentException(
                            "Only JPEG and PNG photos are supported."
                    );
                };

                int width = reader.getWidth(0);
                int height = reader.getHeight(0);

                if (width <= 0 || height <= 0
                        || width > MAX_DIMENSION
                        || height > MAX_DIMENSION
                        || (long) width * height > MAX_PIXELS) {

                    throw new IllegalArgumentException(
                            "The photo dimensions are too large."
                    );
                }

                BufferedImage image = reader.read(0);

                if (image == null) {
                    throw new IllegalArgumentException(
                            "The photo could not be decoded."
                    );
                }

                image.flush();
                return extension;

            } finally {
                reader.dispose();
            }

        } catch (IOException exception) {
            throw new IllegalArgumentException(
                    "The photo is damaged or unreadable.",
                    exception
            );
        }
    }
}