-- V5__product_listing_detail.sql
--
-- Everything the Add Product workspace collects that the baseline listing had no room for:
-- merchandising copy, media, pricing detail, shipping, returns and warranty, and search metadata.
--
-- -- Why these live on the product rather than in their own services ---------------------------
--
-- Stock deliberately does NOT appear here. Inventory Service already owns availableQuantity,
-- reservedQuantity and the low-stock threshold, and a second copy on the product would be a second
-- answer to "how many are there" that drifts the first time a reservation is taken. The Add Product
-- form writes through to that service instead.
--
-- Everything below is different: it describes the product rather than tracking a moving quantity.
-- Weight, return window and meta description are set when the listing is written and change only
-- when a seller edits it, which is exactly the lifecycle of the row they hang off.

ALTER TABLE products
    -- Section 7. Two levels of copy, not one: the short line is what a listing card can afford to
    -- render, the long one is the product page. Deriving the short from the first N characters of
    -- the long produces a sentence cut mid-word on every card in the catalogue.
    ADD COLUMN short_description character varying(300),

    -- Section 6. A digital product has no weight, no dimensions and no delivery estimate, so this
    -- is what lets the form stop asking for them rather than collecting nulls.
    ADD COLUMN product_type character varying(20) NOT NULL DEFAULT 'PHYSICAL',

    -- Section 11. Tax is a property of what is being sold, so it sits with the product; the prices
    -- it applies to sit on the variant, because that is what a customer actually buys.
    ADD COLUMN tax_rate_percent numeric(5, 2),
    ADD COLUMN price_includes_tax boolean NOT NULL DEFAULT true,

    -- Section 18. Millimetres and grams as integers, not decimal metres and kilograms: carrier
    -- rate cards are banded on whole units, and a float here rounds differently in two services.
    ADD COLUMN weight_grams integer,
    ADD COLUMN length_mm integer,
    ADD COLUMN width_mm integer,
    ADD COLUMN height_mm integer,
    ADD COLUMN package_type character varying(40),
    ADD COLUMN shipping_class character varying(40),
    ADD COLUMN free_shipping boolean NOT NULL DEFAULT false,

    -- Section 20. Zero is a real answer meaning "no returns" and is distinct from NULL meaning the
    -- seller has not reached that section yet - the publish checklist needs to tell those apart.
    ADD COLUMN return_window_days integer,
    ADD COLUMN warranty_type character varying(30),
    ADD COLUMN warranty_months integer,

    -- Section 21. Separate from name and description: an SEO title is written for a search result
    -- and is routinely not the product's name, and overwriting one with the other loses the
    -- seller's deliberate choice.
    ADD COLUMN seo_title character varying(200),
    ADD COLUMN meta_description character varying(320),

    -- Section 26/27. What "Saved 12 seconds ago" and "Last edited 2 minutes ago" read from.
    ADD COLUMN last_edited_at timestamp with time zone;

ALTER TABLE products
    ADD CONSTRAINT chk_products_product_type
        CHECK (product_type IN ('PHYSICAL', 'DIGITAL')),
    ADD CONSTRAINT chk_products_tax_rate
        CHECK (tax_rate_percent IS NULL OR (tax_rate_percent >= 0 AND tax_rate_percent <= 100)),
    ADD CONSTRAINT chk_products_return_window
        CHECK (return_window_days IS NULL OR (return_window_days >= 0 AND return_window_days <= 90)),
    ADD CONSTRAINT chk_products_warranty_type
        CHECK (warranty_type IS NULL OR warranty_type IN ('NONE', 'SELLER', 'MANUFACTURER')),
    -- A dimension of zero is not a smaller box, it is an unfilled field that got saved.
    ADD CONSTRAINT chk_products_dimensions
        CHECK ((weight_grams IS NULL OR weight_grams > 0)
           AND (length_mm IS NULL OR length_mm > 0)
           AND (width_mm  IS NULL OR width_mm  > 0)
           AND (height_mm IS NULL OR height_mm > 0));

-- Section 11. Cost price joins the other two money columns on the variant, because margin is per
-- SKU: the 256GB model costs the seller more than the 128GB and shares nothing but a product row.
ALTER TABLE product_variants
    ADD COLUMN cost_price numeric(12, 2),
    ADD CONSTRAINT chk_product_variants_cost_price
        CHECK (cost_price IS NULL OR cost_price >= 0);


-- -- Media -------------------------------------------------------------------------------------
--
-- Sections 8, 9 and 10. A row per asset rather than a JSON array on the product, because ordering
-- is a first-class edit here - the seller drags images to reorder and promotes one to primary - and
-- a rewritten array cannot express "move item 4 to position 1" without rewriting all of it.
--
-- This table stores a URL, never the bytes. Binary in a relational column makes every SELECT that
-- touches the product carry megabytes it did not ask for, and the platform has no object store
-- yet; when one exists, only the writer of this column changes.
CREATE TABLE product_media (
    id bigserial PRIMARY KEY,
    product_id bigint NOT NULL,

    public_id character varying(40) NOT NULL,
    media_type character varying(10) NOT NULL,
    url character varying(1000) NOT NULL,

    -- Section 37. Not defaulted to the product name: alt text repeating the title on all seven
    -- images tells a screen-reader user nothing about any of them.
    alt_text character varying(300),

    -- Section 9. Exactly one primary per product, enforced below rather than by convention.
    is_primary boolean NOT NULL DEFAULT false,
    display_order integer NOT NULL DEFAULT 0,

    -- Section 9's image quality score, stored so the panel does not re-derive it on every render
    -- and so a seller sees the same number they saw yesterday.
    quality_score integer,
    width_px integer,
    height_px integer,
    duration_seconds integer,

    created_at timestamp with time zone NOT NULL DEFAULT now(),

    CONSTRAINT fk_product_media_product
        FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE,
    CONSTRAINT chk_product_media_type
        CHECK (media_type IN ('IMAGE', 'VIDEO')),
    CONSTRAINT chk_product_media_quality
        CHECK (quality_score IS NULL OR (quality_score >= 0 AND quality_score <= 100))
);

CREATE UNIQUE INDEX uq_product_media_public_id ON product_media (public_id);
CREATE INDEX idx_product_media_product ON product_media (product_id, display_order);

-- One primary, or none while the product is still a draft. A partial unique index says exactly
-- that; a CHECK cannot, because the rule spans rows.
CREATE UNIQUE INDEX uq_product_media_one_primary
    ON product_media (product_id) WHERE is_primary;


-- -- Offers ------------------------------------------------------------------------------------
--
-- Section 13. Merchandising badges the seller attaches to this listing - not the coupon engine.
-- A coupon's existence, budget and redemption rules belong to whatever service issues it; what is
-- recorded here is that this product advertises it, and the label customers read.
CREATE TABLE product_offers (
    id bigserial PRIMARY KEY,
    product_id bigint NOT NULL,

    public_id character varying(40) NOT NULL,
    offer_type character varying(20) NOT NULL,
    label character varying(160) NOT NULL,
    code character varying(40),

    -- Section 13's "Ends in 04:32:12". Null means the offer does not expire, which is why the
    -- countdown is driven by this column rather than by the offer type.
    starts_at timestamp with time zone,
    ends_at timestamp with time zone,
    active boolean NOT NULL DEFAULT true,
    display_order integer NOT NULL DEFAULT 0,

    CONSTRAINT fk_product_offers_product
        FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE,
    CONSTRAINT chk_product_offers_type
        CHECK (offer_type IN ('BANK', 'COUPON', 'CASHBACK', 'DEAL')),
    CONSTRAINT chk_product_offers_window
        CHECK (starts_at IS NULL OR ends_at IS NULL OR ends_at > starts_at)
);

CREATE UNIQUE INDEX uq_product_offers_public_id ON product_offers (public_id);
CREATE INDEX idx_product_offers_product ON product_offers (product_id, display_order);


-- -- Search keywords ---------------------------------------------------------------------------
--
-- Section 21's keyword chips. A row per keyword for the same reason product_properties uses one:
-- a comma-joined column forbids commas inside a keyword and cannot be indexed for a match.
CREATE TABLE product_keywords (
    product_id bigint NOT NULL,
    keyword character varying(60) NOT NULL,
    keyword_order integer NOT NULL DEFAULT 0,

    CONSTRAINT fk_product_keywords_product
        FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE,
    CONSTRAINT uq_product_keywords UNIQUE (product_id, keyword)
);

CREATE INDEX idx_product_keywords_product ON product_keywords (product_id);
