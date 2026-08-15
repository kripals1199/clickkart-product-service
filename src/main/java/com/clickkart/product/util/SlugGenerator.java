// src/main/java/com/clickkart/category/util/SlugGenerator.java
package com.clickkart.product.util;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Derives a URL-safe slug from a product name.
 *
 * <p>Normalizes to NFD first so accented characters decompose into a base letter plus a combining
 * mark, and the marks can then be stripped - otherwise "Café" would slug to "caf" and lose a
 * letter rather than becoming "cafe". Indian catalogs routinely carry transliterated names where
 * this matters.
 *
 * <p>A name written entirely in a non-Latin script leaves nothing behind after stripping, so the
 * result can legitimately be empty; callers must handle that rather than assume a usable slug
 * always comes back.
 */
public final class SlugGenerator {

    private SlugGenerator() {}

    public static String slugify(String input) {
        if (input == null) {
            return "";
        }
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return normalized
                .toLowerCase(Locale.ROOT)
                // Anything that is not a lowercase alphanumeric becomes a separator, then runs of
                // separators collapse - so "Men's  Shoes & Bags" yields "men-s-shoes-bags" rather
                // than a string of stray hyphens.
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
    }
}
