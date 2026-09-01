-- V6__brands.sql
--
-- A real brand vocabulary, so "+ Add New Brand" adds something that exists afterwards.
--
-- -- Why a table rather than the free-text column alone -----------------------------------------
--
-- products.brand stays exactly as it is: a denormalised name, already indexed alongside status, and
-- what every catalogue query filters on. This table is not a replacement for it - it is the list of
-- names sellers may pick from.
--
-- The distinction matters because the failure this fixes is spelling, not storage. Forty sellers
-- typing "Samsung", "SAMSUNG" and "Sam sung" produce three brands in the customer-facing filters,
-- and no amount of indexing fixes that after the fact. A shared list, with a normalised key that
-- makes those three the same row, fixes it at the point of entry.
--
-- Deliberately NOT a foreign key from products. A listing that was created before a brand was
-- retired must keep rendering the name it was published with, and a hard reference would either
-- block the retirement or rewrite history.

CREATE TABLE brands (
    id bigserial PRIMARY KEY,
    public_id character varying(40) NOT NULL,

    -- As it should be displayed, with the capitalisation the owner uses.
    name character varying(120) NOT NULL,

    -- Lowercased and stripped of everything but letters and digits. This is what makes "Sam sung",
    -- "SAMSUNG" and "Samsung" collide on the unique index below rather than becoming three brands.
    normalised_name character varying(120) NOT NULL,

    status character varying(20) NOT NULL DEFAULT 'ACTIVE',

    -- Who first used it. A brand a seller invented and a brand an operator curated are different
    -- things when someone later has to decide whether it is real.
    created_by_seller character varying(40),
    seller_created boolean NOT NULL DEFAULT true,

    created_at timestamp with time zone NOT NULL DEFAULT now(),

    CONSTRAINT chk_brands_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE UNIQUE INDEX uq_brands_public_id ON brands (public_id);

-- The whole point of the table.
CREATE UNIQUE INDEX uq_brands_normalised ON brands (normalised_name);

-- The autocomplete reads active names in alphabetical order.
CREATE INDEX idx_brands_status_name ON brands (status, name);
