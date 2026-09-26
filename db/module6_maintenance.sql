-- ==============================================================================
-- CAMS - Campus Asset Management System
-- Module 6: Maintenance Management Schema & DDL
-- Database: Oracle Database (xepdb1)
-- ==============================================================================

SET DEFINE OFF;

-- Drop table if exists
BEGIN
    EXECUTE IMMEDIATE 'DROP TABLE MAINTENANCE CASCADE CONSTRAINTS';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -942 THEN NULL; END IF;
END;
/

CREATE TABLE maintenance (
    maintenance_id    VARCHAR2(50) PRIMARY KEY,
    asset_id          VARCHAR2(50) NOT NULL,
    maintenance_date  DATE DEFAULT SYSDATE NOT NULL,
    scheduled_date    DATE NOT NULL,
    completed_date    DATE,
    fault_description VARCHAR2(500) NOT NULL,
    technician        VARCHAR2(100),
    cost              NUMBER(12, 2) DEFAULT 0.00 NOT NULL CHECK (cost >= 0),
    status            VARCHAR2(30) DEFAULT 'SCHEDULED' NOT NULL 
                      CHECK (status IN ('SCHEDULED', 'IN_PROGRESS', 'COMPLETED', 'REQUIRES_FURTHER_REPAIR')),
    created_by        VARCHAR2(50) NOT NULL,
    issue_id          VARCHAR2(50),
    created_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_maint_asset FOREIGN KEY (asset_id) REFERENCES assets(asset_id),
    CONSTRAINT fk_maint_issue FOREIGN KEY (issue_id) REFERENCES asset_issues(issue_id)
);

CREATE INDEX idx_maint_asset ON maintenance(asset_id);
CREATE INDEX idx_maint_status ON maintenance(status);

COMMIT;
EXIT;
