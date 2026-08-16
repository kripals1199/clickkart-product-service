-- V2__audit_trail.sql
-- The service's own tamper-evident activity log: a hash-chained entry table plus the singleton row
-- holding the tip of the chain.
--
-- This arrives as V2 rather than being folded into V1 because V1 was generated from the live schema
-- before these entities had ever been deployed - so the tables did not exist to be captured. Which is
-- the whole argument for a migration tool: the fix is an additive, reviewed file with a version
-- number, applied in order, instead of editing a baseline other databases have already been stamped
-- against.


CREATE TABLE audit_chain_head (
    id bigint NOT NULL,
    entry_count bigint NOT NULL,
    last_entry_hash character varying(64) NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL
);

CREATE TABLE audit_log_entries (
    id bigint NOT NULL,
    action character varying(40) NOT NULL,
    actor character varying(64) NOT NULL,
    correlation_id character varying(64) NOT NULL,
    details character varying(1000),
    entry_hash character varying(64) NOT NULL,
    ip_address character varying(45) NOT NULL,
    occurred_at timestamp(6) with time zone NOT NULL,
    outcome character varying(10) NOT NULL,
    previous_entry_hash character varying(64) NOT NULL,
    user_agent character varying(512),
    CONSTRAINT audit_log_entries_action_check CHECK (((action)::text = ANY ((ARRAY['PROFILE_CREATED'::character varying, 'PROFILE_UPDATED'::character varying, 'PREFERENCES_UPDATED'::character varying, 'ADDRESS_ADDED'::character varying, 'ADDRESS_UPDATED'::character varying, 'ADDRESS_DELETED'::character varying, 'DEFAULT_ADDRESS_CHANGED'::character varying, 'SELLER_PROFILE_CREATED'::character varying, 'SELLER_PROFILE_UPDATED'::character varying, 'SELLER_VERIFICATION_RESET'::character varying, 'SELLER_VERIFICATION_DECIDED'::character varying, 'PROFILE_ERASED'::character varying])::text[]))),
    CONSTRAINT audit_log_entries_outcome_check CHECK (((outcome)::text = ANY ((ARRAY['SUCCESS'::character varying, 'FAILURE'::character varying])::text[])))
);

CREATE SEQUENCE audit_log_entry_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER TABLE ONLY audit_chain_head
    ADD CONSTRAINT audit_chain_head_pkey PRIMARY KEY (id);

ALTER TABLE ONLY audit_log_entries
    ADD CONSTRAINT audit_log_entries_pkey PRIMARY KEY (id);

CREATE INDEX idx_audit_log_entries_actor ON audit_log_entries USING btree (actor);

CREATE INDEX idx_audit_log_entries_correlation_id ON audit_log_entries USING btree (correlation_id);

CREATE INDEX idx_audit_log_entries_occurred_at ON audit_log_entries USING btree (occurred_at);

