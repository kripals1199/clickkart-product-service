// src/main/java/com/clickkart/product/controller/MediaFileController.java
package com.clickkart.product.controller;

import com.clickkart.product.config.ProductProperties;
import com.clickkart.product.constant.ApiPaths;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves stored product media back.
 *
 * <p>Public, and it has to be: a shopper's browser fetches these while signed out, and an image
 * behind a token would render as a broken frame on every listing card. The filename is a UUID, so
 * an unpublished draft's images are not enumerable even though the route is open.
 *
 * <p>Served through a controller rather than a static resource handler, deliberately. A static
 * handler picks the content type from the file extension, which is exactly the thing an upload
 * endpoint must never trust. Here the type is derived from the suffix <em>this service chose</em>
 * after verifying the bytes, and anything unrecognised is sent as an opaque download rather than
 * something a browser will try to render.
 */
@Slf4j
@Tag(name = "Product Media", description = "Public product images and video")
@RestController
@RequiredArgsConstructor
public class MediaFileController {

    private final ProductProperties productProperties;

    @Operation(summary = "Fetch a stored product image or video")
    @GetMapping(ApiPaths.MEDIA_FILE)
    public ResponseEntity<Resource> fetch(@PathVariable String filename) throws IOException {
        // The stored name is always a UUID plus a known suffix. Anything else did not come from
        // this service, so it is refused before it can be resolved against the directory.
        if (!filename.matches("^[0-9a-fA-F-]{36}\\.[a-z0-9]{2,5}$")) {
            return ResponseEntity.notFound().build();
        }

        Path directory = Path.of(productProperties.getMediaDirectory()).toAbsolutePath().normalize();
        Path target = directory.resolve(filename).normalize();
        if (!target.startsWith(directory) || !Files.isRegularFile(target)) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok()
                .contentType(typeOf(filename))
                .contentLength(Files.size(target))
                // Immutable: the stored name is a UUID, so a given URL's bytes never change. A new
                // image is a new name, which is what makes a long cache safe here.
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                // Belt and braces alongside the global nosniff: even if the type were somehow wrong,
                // nothing here is offered to the browser as a document to execute.
                .header("Content-Security-Policy", "default-src 'none'; sandbox")
                .body(new FileSystemResource(target));
    }

    /** From the suffix this service assigned after verifying the bytes — never a client's claim. */
    private static MediaType typeOf(String filename) {
        String suffix = filename.substring(filename.lastIndexOf('.') + 1);
        return switch (suffix) {
            case "jpg" -> MediaType.IMAGE_JPEG;
            case "png" -> MediaType.IMAGE_PNG;
            case "gif" -> MediaType.IMAGE_GIF;
            case "webp" -> MediaType.parseMediaType("image/webp");
            case "mp4" -> MediaType.parseMediaType("video/mp4");
            default -> MediaType.APPLICATION_OCTET_STREAM;
        };
    }
}
