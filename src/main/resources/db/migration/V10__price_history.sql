-- What a variant has cost over time, so "price dropped 12%" is a fact rather than a decoration.
--
-- Keyed by (product_id, sku), NOT by a foreign key to product_variants. That looks like the weaker
-- design and is the correct one here: updateOwnProduct replaces a product's variants wholesale, so
-- orphanRemoval deletes and recreates the rows on every edit. A FK to a variant would take the
-- price history with it the first time a seller changed a title. The SKU survives that, because it
-- is what the seller keeps.
--
-- One row per observed change, not per save. A seller who edits a listing ten times without
-- touching the price leaves one row, so "the lowest it has been" stays a question about prices
-- rather than about how often somebody opened the form.

CREATE TABLE product_price_history (
    id bigint NOT NULL,
    created_by character varying(100) NOT NULL,
    created_date timestamp(6) with time zone NOT NULL,
    updated_by character varying(100) NOT NULL,
    updated_date timestamp(6) with time zone NOT NULL,
    version bigint NOT NULL,
    product_id bigint NOT NULL,
    sku character varying(60) NOT NULL,
    selling_price numeric(12, 2) NOT NULL,
    recorded_at timestamp(6) with time zone NOT NULL,
    CONSTRAINT product_price_history_pkey PRIMARY KEY (id),
    CONSTRAINT fk_product_price_history_product FOREIGN KEY (product_id) REFERENCES products(id)
);

-- The two reads this table serves: "what did this SKU last cost" (newest first, one row) and
-- "what is the highest it has been since <date>" (a range scan). Both are covered by this.
CREATE INDEX idx_product_price_history_sku_time
    ON product_price_history (product_id, sku, recorded_at DESC);
