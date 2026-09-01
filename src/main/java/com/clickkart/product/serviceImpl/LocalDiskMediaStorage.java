// src/main/java/com/clickkart/product/serviceImpl/LocalDiskMediaStorage.java
package com.clickkart.product.serviceImpl;

import com.clickkart.product.config.ProductProperties;
import com.clickkart.product.service.MediaStorage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import javax.imageio.ImageIO;
import javax.imageio.stream.ImageInputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Writes uploaded media to a directory on this host.
 *
 * <p>The pragmatic implementation until the platform has an object store. It is deliberately behind
 * {@link MediaStorage} so that replacing it changes one class: nothing that reads a listing knows
 * where the bytes live.
 *
 * <p><strong>What this defends against.</strong> An upload endpoint is the largest piece of
 * attacker-controlled input a seller-facing service accepts, and every one of these is a real
 * attack rather than a theoretical one:
 *
 * <ul>
 *   <li><em>Path traversal.</em> The stored name is a generated UUID plus a suffix chosen from a
 *       fixed table. The browser's filename never reaches the filesystem, so {@code ../../} in it
 *       has nothing to act on.
 *   <li><em>Content-type lying.</em> {@code Content-Type} is whatever the client says. A polyglot
 *       file that is a valid GIF and a valid script is served back to customers, so the type is
 *       determined by reading the leading bytes and the declared one is only cross-checked.
 *   <li><em>Decompression bombs.</em> A small file can declare enormous dimensions. The header is
 *       read for width and height before any attempt to decode the pixels, and absurd dimensions
 *       are refused without allocating a raster for them.
 *   <li><em>SVG.</em> Not accepted at all. SVG is a document format that executes script in the
 *       browsers that render it, and served from our origin it is stored XSS.
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LocalDiskMediaStorage implements MediaStorage {

    /**
     * Accepted types, and the leading bytes that prove each one.
     *
     * <p>A fixed allow-list rather than a deny-list: a deny-list is wrong the moment a new format
     * appears, and the cost of being wrong here is a file we serve back to every customer.
     */
    private static final List<Signature> SIGNATURES = List.of(
            new Signature("image/jpeg", ".jpg", new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}),
            new Signature("image/png", ".png",
                    new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A}),
            new Signature("image/gif", ".gif", new byte[] {'G', 'I', 'F', '8'}),
            new Signature("image/webp", ".webp", new byte[] {'R', 'I', 'F', 'F'}),
            new Signature("video/mp4", ".mp4", new byte[] {0x00, 0x00, 0x00}));

    /** Beyond this a JPEG is not a product photo, it is someone probing for a memory limit. */
    private static final int MAX_DIMENSION_PX = 12_000;

    private final ProductProperties productProperties;

    @Override
    public StoredMedia store(byte[] content, String originalFilename, String declaredContentType) {
        if (content == null || content.length == 0) {
            throw new MediaRejectedException("The file is empty.");
        }
        long maxBytes = productProperties.getMaxMediaBytes();
        if (content.length > maxBytes) {
            throw new MediaRejectedException(
                    "This file is larger than the " + (maxBytes / (1024 * 1024)) + "MB limit.");
        }

        Signature signature = signatureOf(content)
                .orElseThrow(() -> new MediaRejectedException(
                        // Named rather than vague, per section 31: the seller can act on "convert it
                        // to JPEG", not on "invalid input".
                        "That file is not a JPEG, PNG, GIF, WebP or MP4."));

        // The declared type is not trusted, but a disagreement is still worth recording: it is
        // either a misconfigured client or someone establishing what we check.
        if (declaredContentType != null && !declaredContentType.isBlank()
                && !declaredContentType.toLowerCase(Locale.ROOT).startsWith(signature.contentType())) {
            log.warn("Upload declared {} but the bytes are {}; storing as the latter. name={}",
                    declaredContentType, signature.contentType(), safeName(originalFilename));
        }

        Dimensions dimensions = readDimensions(content);

        // Generated, never derived from what the browser sent.
        String storedName = UUID.randomUUID() + signature.suffix();
        Path directory = Path.of(productProperties.getMediaDirectory()).toAbsolutePath().normalize();
        Path target = directory.resolve(storedName).normalize();

        // Belt and braces. The name is ours and cannot escape, but a future change to how it is
        // built should fail here rather than silently write outside the directory.
        if (!target.startsWith(directory)) {
            throw new MediaRejectedException("That file could not be stored.");
        }

        try {
            Files.createDirectories(directory);
            try (ByteArrayInputStream in = new ByteArrayInputStream(content)) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            log.error("Could not write media to {}", target, e);
            throw new MediaRejectedException("We couldn't upload this image. Try again.");
        }

        String url = productProperties.getMediaBaseUrl() + "/" + storedName;
        log.info("Stored media {} ({} bytes, {})", storedName, content.length, signature.contentType());
        return new StoredMedia(
                url, signature.contentType(), dimensions.width(), dimensions.height(), content.length);
    }

    @Override
    public void delete(String url) {
        if (url == null || url.isBlank()) {
            return;
        }
        String name = url.substring(url.lastIndexOf('/') + 1);
        // Anything with a separator in it did not come from store(), so it is not ours to delete.
        if (name.isBlank() || name.contains("..") || name.contains("/") || name.contains("\\")) {
            log.warn("Refusing to delete media by a name that did not come from this store: {}", url);
            return;
        }
        Path directory = Path.of(productProperties.getMediaDirectory()).toAbsolutePath().normalize();
        Path target = directory.resolve(name).normalize();
        if (!target.startsWith(directory)) {
            return;
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            // A listing that no longer references the file is the state that matters; an orphaned
            // blob costs disk, not correctness.
            log.warn("Could not delete media {}", target, e);
        }
    }

    /** Matches the leading bytes against the allow-list. */
    private static java.util.Optional<Signature> signatureOf(byte[] content) {
        return SIGNATURES.stream()
                .filter(candidate -> startsWith(content, candidate.magic()))
                .findFirst()
                // MP4's marker sits at offset 4, not 0, so it is checked separately rather than
                // giving every other format a needless offset parameter.
                .or(() -> isMp4(content)
                        ? SIGNATURES.stream().filter(s -> s.suffix().equals(".mp4")).findFirst()
                        : java.util.Optional.empty());
    }

    private static boolean startsWith(byte[] content, byte[] magic) {
        if (content.length < magic.length) {
            return false;
        }
        return Arrays.equals(content, 0, magic.length, magic, 0, magic.length);
    }

    private static boolean isMp4(byte[] content) {
        return content.length > 12
                && content[4] == 'f' && content[5] == 't' && content[6] == 'y' && content[7] == 'p';
    }

    /**
     * Reads width and height from the header without decoding the image.
     *
     * <p>{@code ImageIO.read} would allocate the full raster, which is exactly what a decompression
     * bomb wants. The reader interface exposes the dimensions from the header alone.
     */
    private Dimensions readDimensions(byte[] content) {
        try (ImageInputStream stream = ImageIO.createImageInputStream(new ByteArrayInputStream(content))) {
            if (stream == null) {
                return new Dimensions(null, null);
            }
            var readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) {
                return new Dimensions(null, null);
            }
            var reader = readers.next();
            try {
                reader.setInput(stream);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width > MAX_DIMENSION_PX || height > MAX_DIMENSION_PX) {
                    throw new MediaRejectedException(
                            "That image is " + width + "x" + height + ", which is larger than we can process.");
                }
                return new Dimensions(width, height);
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            // A video, or a format with no reader. Not fatal - dimensions are advisory.
            return new Dimensions(null, null);
        }
    }

    /** Never log an attacker-supplied filename raw: it reaches a log viewer someone else reads. */
    private static String safeName(String filename) {
        if (filename == null) {
            return "(none)";
        }
        String trimmed = filename.length() > 80 ? filename.substring(0, 80) : filename;
        return trimmed.replaceAll("[\\p{Cntrl}\\r\\n]", "_");
    }

    private record Signature(String contentType, String suffix, byte[] magic) {}

    private record Dimensions(Integer width, Integer height) {}
}
