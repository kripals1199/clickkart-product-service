-- Customer reviews, and the rating aggregate they feed.
--
-- The aggregate lives on products rather than being computed per read. A listing page shows stars
-- on every tile, and a correlated avg() per row turns one query into one-plus-N. It is recomputed
-- from this table on every review write, so it is exact rather than eventually consistent - drift
-- in a number a shopper uses to choose is worse than the write costing one extra statement.

CREATE TABLE product_reviews (
    id bigint NOT NULL,
    created_by character varying(100) NOT NULL,
    created_date timestamp(6) with time zone NOT NULL,
    updated_by character varying(100) NOT NULL,
    updated_date timestamp(6) with time zone NOT NULL,
    version bigint NOT NULL,
    public_id character varying(40) NOT NULL,
    product_id bigint NOT NULL,
    author_public_id character varying(40) NOT NULL,
    author_display_name character varying(80),
    rating smallint NOT NULL,
    title character varying(120),
    body character varying(4000),
    verified_purchase boolean NOT NULL DEFAULT false,
    status character varying(20) NOT NULL,
    hidden_reason character varying(200),
    CONSTRAINT product_reviews_pkey PRIMARY KEY (id),
    CONSTRAINT product_reviews_rating_check CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT product_reviews_status_check
        CHECK (status IN ('PUBLISHED', 'HIDDEN')),
    CONSTRAINT fk_product_reviews_product FOREIGN KEY (product_id) REFERENCES products(id)
);

CREATE UNIQUE INDEX uq_product_reviews_public_id ON product_reviews (public_id);

-- One review per person per product, enforced by the database rather than by a check in the
-- service: two submits from two tabs both read "no review yet" and both insert. The index makes
-- the loser fail, and the service turns that into an update of what is already there.
CREATE UNIQUE INDEX uq_product_reviews_product_author
    ON product_reviews (product_id, author_public_id);

-- The listing query is "published reviews for this product, newest first".
CREATE INDEX idx_product_reviews_product_status
    ON product_reviews (product_id, status, created_date DESC);

ALTER TABLE products ADD COLUMN rating_average numeric(3, 2);
ALTER TABLE products ADD COLUMN rating_count integer NOT NULL DEFAULT 0;

-- Nullable average, not zero. A product with no reviews has no rating, and 0.00 would sort it
-- below a genuinely bad one and render as zero stars rather than as "not rated yet".
COMMENT ON COLUMN products.rating_average IS 'NULL until the first published review';
