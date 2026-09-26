-- ==============================================================================
-- CAMS - Campus Asset Management System
-- Module 8: Inventory Audit Schema & DDL
-- Database: Oracle Database (xepdb1)
-- ==============================================================================

SET DEFINE OFF;

-- Drop table if exists
BEGIN
    EXECUTE IMMEDIATE 'DROP TABLE INVENTORY_AUDITS CASCADE CONSTRAINTS';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -942 THEN NULL; END IF;
END;
/

CREATE TABLE inventory_audits (
    audit_id     VARCHAR2(50) PRIMARY KEY,
    asset_id     VARCHAR2(50) NOT NULL,
    audit_date   TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    verified_by  VARCHAR2(50) NOT NULL,
    status       VARCHAR2(20) NOT NULL CHECK (status IN ('VERIFIED', 'MISSING', 'MISLOCATED')),
    remarks      VARCHAR2(255),
    created_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT fk_audit_asset FOREIGN KEY (asset_id) REFERENCES assets(asset_id),
    CONSTRAINT fk_audit_user FOREIGN KEY (verified_by) REFERENCES users(username)
);

CREATE INDEX idx_audit_asset ON inventory_audits(asset_id);
CREATE INDEX idx_audit_date ON inventory_audits(audit_date);
CREATE INDEX idx_audit_status ON inventory_audits(status);

COMMIT;
EXIT;
