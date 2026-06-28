-- V2: Make monitoring_audit_logs.user_id nullable
-- AuditService records system events where userId is not always available
ALTER TABLE monitoring_audit_logs ALTER COLUMN user_id VARCHAR(36);
