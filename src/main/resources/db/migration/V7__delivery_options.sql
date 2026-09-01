-- V7__delivery_options.sql
--
-- Section 18. Which delivery speeds a listing supports.
--
-- -- Why a table rather than a column -----------------------------------------------------------
--
-- A product can offer both standard and express; a seller of something bulky may offer only
-- standard. That is a set, not a choice, so a single `delivery_option` column would either forbid
-- offering both or become a comma-joined string nobody can index or filter on.
--
-- Free shipping is deliberately NOT one of these values. It already exists as products.free_shipping
-- and it answers a different question - "what does delivery cost" rather than "how fast is it".
-- Folding the two together would make "free" and "express" mutually exclusive, which they are not.

CREATE TABLE product_delivery_options (
    product_id bigint NOT NULL,
    delivery_option character varying(20) NOT NULL,

    CONSTRAINT fk_product_delivery_options_product
        FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE,
    CONSTRAINT uq_product_delivery_options UNIQUE (product_id, delivery_option),
    CONSTRAINT chk_product_delivery_options
        CHECK (delivery_option IN ('STANDARD', 'EXPRESS'))
);

CREATE INDEX idx_product_delivery_options_product ON product_delivery_options (product_id);

-- Every physical listing that already exists ships at standard speed - that was the only thing on
-- offer before this table. Stating it explicitly beats leaving the set empty, which the form would
-- read as "this product cannot be delivered at all".
INSERT INTO product_delivery_options (product_id, delivery_option)
SELECT id, 'STANDARD' FROM products WHERE product_type = 'PHYSICAL';
