-- V4__product_properties.sql
--
-- Where the values a seller enters against a category's master-data properties are kept.
--
-- Category Service decides *which* properties apply to a category and what they accept; this table
-- holds what a seller actually said. The two are deliberately in different services: the structure
-- is catalogue governance and changes rarely, the values belong to the product and change with it.
-- The only thing crossing the boundary is `property_name`, which Category Service guarantees is
-- stable and never renamed.
--
-- ── Why a row per value rather than a map column ─────────────────────────────────────────────
--
-- product_variant_attributes next door is a Map<String,String>, and mirroring it here would have
-- been the smaller change. Two things make it the wrong shape for this table.
--
-- First, multi-valued properties. "Connectivity = Wi-Fi, Bluetooth, NFC" is three answers, not one
-- comma-joined string. Storing it joined means every reader has to split it, and the separator is
-- then forbidden inside a value forever.
--
-- Second, and the real reason: these values are what customer-facing filters are built from. One
-- row per value makes "every product where RAM = 8" an indexed equality match. Against a joined
-- string it is a LIKE over a substring, which cannot use an index and matches "128" when asked for
-- "12".

CREATE TABLE product_properties (
    product_id bigint NOT NULL,

    -- The stable machine identifier from Category Service - never its display label, which is
    -- editable there and would orphan every value recorded under the old text.
    property_name character varying(80) NOT NULL,

    -- The canonical stored form. For a controlled property this is the `value` of one of its
    -- allowed values, not the label: "8", not "8 GB". The unit lives on the property definition, so
    -- storing it here would both duplicate it and make the value non-numeric.
    property_value character varying(500) NOT NULL,

    -- Position within a multi-valued property, so "Wi-Fi, Bluetooth, NFC" keeps the order the
    -- seller chose. Zero for single-valued properties.
    value_order integer DEFAULT 0 NOT NULL
);

ALTER TABLE ONLY product_properties
    ADD CONSTRAINT fk_product_properties_product FOREIGN KEY (product_id)
        REFERENCES products(id) ON DELETE CASCADE;

-- A product may hold the same property name several times (multi-select) but not the same value
-- twice under one name - that would be a duplicate answer, and would double-count in a facet.
ALTER TABLE ONLY product_properties
    ADD CONSTRAINT uk_product_properties_value UNIQUE (product_id, property_name, property_value);

-- Reading a product's specifications: everything for one product, in order.
CREATE INDEX idx_product_properties_product ON product_properties USING btree (product_id, property_name, value_order);

-- The filter query, from the other direction: every product answering RAM = 8. This is the index
-- that makes the whole filterable-properties feature possible, and the reason for the table shape.
CREATE INDEX idx_product_properties_lookup ON product_properties USING btree (property_name, property_value);
