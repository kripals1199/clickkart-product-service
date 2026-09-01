// src/main/java/com/clickkart/product/constant/ApiPaths.java
package com.clickkart.product.constant;

/**
 * Route strings, grouped by who may call them.
 *
 * <p>Three audiences, three roots: anonymous customers browse {@link #BASE}, a seller manages their
 * own listings under {@link #SELLER_BASE}, and operators moderate under {@link #ADMIN_BASE}.
 * Separating them by path rather than by verb alone means "which routes can a stranger reach" is
 * answerable by reading the paths, and the security rules stay short enough to check by eye.
 */
public final class ApiPaths {

    private ApiPaths() {}

    public static final String BASE = "/api/v1/products";

    /*
     * Literal segments (search, slug, seller, admin) sit alongside the {publicId} template. Spring
     * resolves a literal ahead of a template, and a publicId is always "PRD-<uuid>", so no real id
     * can collide with one. Both facts must stay true if the id format ever changes.
     */
    public static final String SEARCH = BASE + "/search";
    public static final String BY_SLUG = BASE + "/slug/{slug}";
    public static final String BY_PUBLIC_ID = BASE + "/{publicId}";

    /** Seller-scoped. Always acts on the token's own subject; no seller id appears in any path. */
    public static final String SELLER_BASE = BASE + "/seller";
    public static final String SELLER_PRODUCT = SELLER_BASE + "/{publicId}";
    public static final String SELLER_SUBMIT = SELLER_BASE + "/{publicId}/submission";
    public static final String SELLER_ARCHIVE = SELLER_BASE + "/{publicId}/archive";
    public static final String SELLER_VARIANTS = SELLER_BASE + "/{publicId}/variants";
    public static final String SELLER_VARIANT = SELLER_BASE + "/{publicId}/variants/{sku}";

    /* ---- sections 8 to 10 and 13: the Add Product workspace ---- */

    public static final String SELLER_MEDIA_UPLOAD = SELLER_BASE + "/media";
    public static final String SELLER_MEDIA_LIBRARY = SELLER_BASE + "/media/library";
    public static final String SELLER_MEDIA = SELLER_BASE + "/{publicId}/media";
    public static final String SELLER_MEDIA_ONE = SELLER_MEDIA + "/{mediaPublicId}";
    public static final String SELLER_MEDIA_ORDER = SELLER_MEDIA + "/order";
    public static final String SELLER_MEDIA_PRIMARY = SELLER_MEDIA_ONE + "/primary";
    public static final String SELLER_MEDIA_FILE = SELLER_MEDIA_ONE + "/file";

    public static final String SELLER_OFFERS = SELLER_BASE + "/{publicId}/offers";
    public static final String SELLER_OFFER_ONE = SELLER_OFFERS + "/{offerPublicId}";

    /** Where stored media is served back from. Public - a product image is not a secret. */
    public static final String MEDIA_FILE = BASE + "/media/{filename}";

    /**
     * Reviews. Reading is public - a shopper deciding whether to buy is exactly who reviews are
     * for, and hiding them behind a sign-in would make the ratings on the listing pages
     * unexplainable. Writing needs a session, because a review has an author.
     */
    /**
     * How far this listing's price has fallen from its high in the window.
     *
     * <p>Its own endpoint rather than a field on the product, and asked only by the detail page.
     * A listing grid shows dozens of products at once, and a per-product history query behind each
     * tile is the one-plus-N this codebase avoids elsewhere. A shopper reading one product can
     * afford one more query; a shopper scanning a grid cannot afford thirty.
     */
    public static final String PRICE_DROP = BY_PUBLIC_ID + "/price-drop";

    public static final String REVIEWS = BY_PUBLIC_ID + "/reviews";
    public static final String REVIEW_SUMMARY = REVIEWS + "/summary";
    public static final String REVIEW_MINE = REVIEWS + "/mine";

    /** Operator actions on a single review, addressed by its own id rather than the product's. */
    public static final String ADMIN_REVIEW_HIDE = BASE + "/admin/reviews/{reviewPublicId}/hidden";

    /** Section 6. Reading is public - the customer brand filter needs the same list. */
    public static final String BRANDS = "/api/v1/brands";

    /** ADMIN moderation. */
    public static final String ADMIN_BASE = BASE + "/admin";
    public static final String ADMIN_QUEUE = ADMIN_BASE + "/review-queue";
    public static final String ADMIN_DECISION = ADMIN_BASE + "/{publicId}/review";

    /** Service-to-service. No Gateway route; shared-secret authenticated. */
    public static final String INTERNAL_BASE = "/internal/v1/products";
    public static final String INTERNAL_WILDCARD = "/internal/**";
    public static final String INTERNAL_PRODUCT = INTERNAL_BASE + "/{publicId}";
    public static final String INTERNAL_VARIANT_BY_SKU = INTERNAL_BASE + "/variants/{sku}";

    /** This service's own tamper-evident activity log. ADMIN only. */
    public static final String ADMIN_AUDIT = BASE + "/audit";
    public static final String ADMIN_AUDIT_VERIFY = ADMIN_AUDIT + "/verification";

    public static final String ACTUATOR_HEALTH = "/actuator/health";
    public static final String ACTUATOR_HEALTH_WILDCARD = "/actuator/health/**";
    public static final String ACTUATOR_PROMETHEUS = "/actuator/prometheus";
    public static final String SWAGGER_UI = "/swagger-ui.html";
    public static final String SWAGGER_UI_WILDCARD = "/swagger-ui/**";
    public static final String API_DOCS_WILDCARD = "/v3/api-docs/**";
}
