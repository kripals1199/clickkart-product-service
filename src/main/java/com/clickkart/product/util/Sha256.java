// src/main/java/com/clickkart/product/util/Sha256.java
package com.clickkart.product.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * SHA-256 for the audit hash chain. Own copy per service (Rule 4 - no shared library).
 */
public final class Sha256 {

    private static final String ALGORITHM = "SHA-256";

    private Sha256() {}

    /** @return the lowercase hex-encoded SHA-256 digest of {@code input} (64 characters) */
    public static String hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(ALGORITHM + " algorithm unavailable", e);
        }
    }
}
