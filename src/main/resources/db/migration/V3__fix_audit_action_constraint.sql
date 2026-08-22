-- V3__fix_audit_action_constraint.sql
-- Lets this service write its own audit trail.
--
-- V2__audit_trail.sql was copied from User Service, and the CHECK constraint on
-- audit_log_entries.action came with it - still listing that service's vocabulary
-- (PROFILE_CREATED, ADDRESS_ADDED, DEFAULT_ADDRESS_CHANGED) rather than this one's. The enum here
-- and the constraint in the database therefore had no value in common, so every audited write in
-- this service failed at commit:
--
--   ERROR: new row for relation "audit_log_entries" violates check constraint
--          "audit_log_entries_action_check"
--
-- Because the audit insert shares the transaction with the write it records, the write rolled back
-- with it. The symptom was a 500 from an operation that looked entirely reasonable, with the real
-- cause buried in the service log - a product that got as far as being assigned a public id before
-- vanishing. Five services carried the same paste; only User Service, the original, was correct.
--
-- The list below is generated from ProductAuditAction rather than typed out, because
-- hand-copying an action list is exactly what went wrong the first time. Adding a value to that
-- enum still needs a migration - the constraint cannot follow the code on its own.

ALTER TABLE audit_log_entries
    DROP CONSTRAINT IF EXISTS audit_log_entries_action_check;

ALTER TABLE audit_log_entries
    ADD CONSTRAINT audit_log_entries_action_check CHECK (
        action IN (
        'PRODUCT_CREATED',
        'PRODUCT_UPDATED',
        'PRODUCT_SUBMITTED_FOR_REVIEW',
        'PRODUCT_APPROVED',
        'PRODUCT_REJECTED',
        'PRODUCT_ARCHIVED',
        'VARIANT_ADDED',
        'VARIANT_UPDATED'
    ));
