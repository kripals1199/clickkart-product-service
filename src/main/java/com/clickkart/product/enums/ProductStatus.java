// src/main/java/com/clickkart/product/enums/ProductStatus.java
package com.clickkart.product.enums;

/**
 * Where a listing sits in the moderation workflow.
 *
 * <p>The states exist because a marketplace lets third parties publish, and letting a seller put a
 * listing straight in front of customers is how counterfeit and mispriced goods reach a storefront.
 * Only {@link #ACTIVE} is publicly visible; everything else is seller- or operator-only.
 *
 * <pre>
 *   DRAFT ──submit──▶ PENDING_REVIEW ──approve──▶ ACTIVE ──archive──▶ ARCHIVED
 *     ▲                    │                        │                    │
 *     └──────reject────────┘                        └──────archive───────┘
 * </pre>
 *
 * A rejected listing returns to {@link #DRAFT} rather than a dead end, so the seller can fix what
 * the operator flagged and resubmit.
 */
public enum ProductStatus {
    /** Seller is still editing. Never publicly visible, and freely editable. */
    DRAFT,
    /** Submitted, awaiting an operator. Frozen against seller edits so it cannot change under review. */
    PENDING_REVIEW,
    /** Approved and on sale. */
    ACTIVE,
    /** Withdrawn from sale, by the seller or an operator. Kept because orders reference it. */
    ARCHIVED
}
