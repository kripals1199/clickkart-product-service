-- V1__baseline.sql
-- Generated from the live schema Hibernate's ddl-auto produced, so this is exactly what already
-- exists rather than a hand-written approximation of it.
--
-- Existing databases are baselined at V1 and skip this file (spring.flyway.baseline-version=1).
-- A fresh database gets its whole schema from here - which is the point: the schema becomes a
-- reviewed artefact in git rather than a side effect of whatever the entity classes happened to
-- look like the last time the application started.


CREATE SEQUENCE clickkart_product_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE product_variant_attributes (
    variant_id bigint NOT NULL,
    attribute_value character varying(200),
    attribute_key character varying(60) NOT NULL
);

CREATE TABLE product_variants (
    id bigint NOT NULL,
    created_by character varying(100) NOT NULL,
    created_date timestamp(6) with time zone NOT NULL,
    updated_by character varying(100) NOT NULL,
    updated_date timestamp(6) with time zone NOT NULL,
    version bigint NOT NULL,
    active boolean NOT NULL,
    mrp numeric(12,2) NOT NULL,
    selling_price numeric(12,2) NOT NULL,
    sku character varying(64) NOT NULL,
    variant_name character varying(150) NOT NULL,
    product_id bigint NOT NULL
);

CREATE TABLE products (
    id bigint NOT NULL,
    created_by character varying(100) NOT NULL,
    created_date timestamp(6) with time zone NOT NULL,
    updated_by character varying(100) NOT NULL,
    updated_date timestamp(6) with time zone NOT NULL,
    version bigint NOT NULL,
    brand character varying(120),
    category_public_id character varying(40) NOT NULL,
    description character varying(4000),
    name character varying(200) NOT NULL,
    public_id character varying(40) NOT NULL,
    rejection_reason character varying(500),
    reviewed_at timestamp(6) with time zone,
    reviewed_by character varying(64),
    seller_public_id character varying(64) NOT NULL,
    slug character varying(220) NOT NULL,
    status character varying(20) NOT NULL,
    CONSTRAINT products_status_check CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'PENDING_REVIEW'::character varying, 'ACTIVE'::character varying, 'ARCHIVED'::character varying])::text[])))
);

ALTER TABLE ONLY product_variant_attributes
    ADD CONSTRAINT product_variant_attributes_pkey PRIMARY KEY (variant_id, attribute_key);

ALTER TABLE ONLY product_variants
    ADD CONSTRAINT product_variants_pkey PRIMARY KEY (id);

ALTER TABLE ONLY products
    ADD CONSTRAINT products_pkey PRIMARY KEY (id);

ALTER TABLE ONLY product_variants
    ADD CONSTRAINT uk_product_variants_sku UNIQUE (sku);

ALTER TABLE ONLY products
    ADD CONSTRAINT uk_products_public_id UNIQUE (public_id);

ALTER TABLE ONLY products
    ADD CONSTRAINT uk_products_slug UNIQUE (slug);

CREATE INDEX idx_product_variants_product_id ON product_variants USING btree (product_id);

CREATE INDEX idx_product_variants_sku ON product_variants USING btree (sku);

CREATE INDEX idx_products_category ON products USING btree (category_public_id);

CREATE INDEX idx_products_seller ON products USING btree (seller_public_id);

CREATE INDEX idx_products_status_brand ON products USING btree (status, brand);

CREATE INDEX idx_products_status_category ON products USING btree (status, category_public_id);

CREATE INDEX idx_variant_attributes_variant_id ON product_variant_attributes USING btree (variant_id);

ALTER TABLE ONLY product_variant_attributes
    ADD CONSTRAINT fkjcp7i8t08la8masfe513eisnv FOREIGN KEY (variant_id) REFERENCES product_variants(id);

ALTER TABLE ONLY product_variants
    ADD CONSTRAINT fkosqitn4s405cynmhb87lkvuau FOREIGN KEY (product_id) REFERENCES products(id);

