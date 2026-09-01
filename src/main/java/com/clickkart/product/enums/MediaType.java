// src/main/java/com/clickkart/product/enums/MediaType.java
package com.clickkart.product.enums;

/**
 * What a media row points at.
 *
 * <p>Sections 8 to 10. Kept on one table rather than two because ordering is shared - a video sits
 * in the same gallery strip as the images and the seller drags it into place among them.
 */
public enum MediaType {
    IMAGE,
    /** Optional throughout, and never eligible to be the primary asset. */
    VIDEO
}
