-- V8__audit_columns_on_new_tables.sql
--
-- Gives product_media, product_offers and brands the audit columns BaseEntity requires.
--
-- -- What went wrong ---------------------------------------------------------------------------
--
-- All three entities extend BaseEntity, which maps created_by, created_date, updated_by,
-- updated_date and version. V5 and V6 created the tables with a single created_at column instead,
-- which is not one of them. Hibernate is configured with ddl-auto=validate, so the application
-- refused to start:
--
--     Schema validation: missing column [created_by] in table [brands]
--
-- No unit test could have caught this. The mismatch is between a migration and an entity mapping,
-- and both were individually correct - it only exists when the two meet a real database.
--
-- -- Why a new migration rather than fixing V5 and V6 -------------------------------------------
--
-- V5, V6 and V7 have already been applied. Editing them changes their checksum, and Flyway then
-- refuses to start against any database that ran the originals - which now includes this one.
-- Correcting forward is the only option that leaves both a fresh database and an existing one in
-- the same state.

-- -- product_media --------------------------------------------------------------------------

ALTER TABLE product_media
    ADD COLUMN created_by character varying(100),
    ADD COLUMN created_date timestamp(6) with time zone,
    ADD COLUMN updated_by character varying(100),
    ADD COLUMN updated_date timestamp(6) with time zone,
    ADD COLUMN version bigint;

-- created_at held the same instant, so it seeds both timestamps rather than being discarded.
UPDATE product_media
   SET created_by = COALESCE(created_by, 'system'),
       created_date = COALESCE(created_date, created_at, now()),
       updated_by = COALESCE(updated_by, 'system'),
       updated_date = COALESCE(updated_date, created_at, now()),
       version = COALESCE(version, 0);

ALTER TABLE product_media
    ALTER COLUMN created_by SET NOT NULL,
    ALTER COLUMN created_date SET NOT NULL,
    ALTER COLUMN updated_by SET NOT NULL,
    ALTER COLUMN updated_date SET NOT NULL,
    ALTER COLUMN version SET NOT NULL,
    -- Superseded by created_date. Leaving it would be a second answer to when the row was made.
    DROP COLUMN created_at;

-- -- product_offers -------------------------------------------------------------------------

ALTER TABLE product_offers
    ADD COLUMN created_by character varying(100),
    ADD COLUMN created_date timestamp(6) with time zone,
    ADD COLUMN updated_by character varying(100),
    ADD COLUMN updated_date timestamp(6) with time zone,
    ADD COLUMN version bigint;

UPDATE product_offers
   SET created_by = COALESCE(created_by, 'system'),
       created_date = COALESCE(created_date, now()),
       updated_by = COALESCE(updated_by, 'system'),
       updated_date = COALESCE(updated_date, now()),
       version = COALESCE(version, 0);

ALTER TABLE product_offers
    ALTER COLUMN created_by SET NOT NULL,
    ALTER COLUMN created_date SET NOT NULL,
    ALTER COLUMN updated_by SET NOT NULL,
    ALTER COLUMN updated_date SET NOT NULL,
    ALTER COLUMN version SET NOT NULL;

-- -- brands ---------------------------------------------------------------------------------

ALTER TABLE brands
    ADD COLUMN created_by character varying(100),
    ADD COLUMN created_date timestamp(6) with time zone,
    ADD COLUMN updated_by character varying(100),
    ADD COLUMN updated_date timestamp(6) with time zone,
    ADD COLUMN version bigint;

UPDATE brands
   SET created_by = COALESCE(created_by, 'system'),
       created_date = COALESCE(created_date, created_at, now()),
       updated_by = COALESCE(updated_by, 'system'),
       updated_date = COALESCE(updated_date, created_at, now()),
       version = COALESCE(version, 0);

ALTER TABLE brands
    ALTER COLUMN created_by SET NOT NULL,
    ALTER COLUMN created_date SET NOT NULL,
    ALTER COLUMN updated_by SET NOT NULL,
    ALTER COLUMN updated_date SET NOT NULL,
    ALTER COLUMN version SET NOT NULL,
    DROP COLUMN created_at;
