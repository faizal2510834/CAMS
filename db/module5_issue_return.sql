-- ==============================================================================
-- CAMS - Campus Asset Management System
-- Module 5: Issue & Return Management Schema
-- Database: Oracle Database (xepdb1)
-- ==============================================================================

SET DEFINE OFF;

-- 1. Drop ASSET_ISSUES table if present for idempotency
BEGIN
    EXECUTE IMMEDIATE 'DROP TABLE ASSET_ISSUES CASCADE CONSTRAINTS';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -942 THEN NULL; END IF;
END;
/

-- 2. Create ASSET_ISSUES table
CREATE TABLE asset_issues (
    issue_id              VARCHAR2(50) PRIMARY KEY,
    asset_id              VARCHAR2(50) NOT NULL REFERENCES assets(asset_id),
    issued_to_user_id     NUMBER(10) NOT NULL REFERENCES users(user_id),
    issued_to_department  VARCHAR2(50) NOT NULL REFERENCES departments(department_id),
    issue_date            DATE NOT NULL,
    expected_return_date  DATE,
    actual_return_date    DATE,
    status                VARCHAR2(20) DEFAULT 'ISSUED' NOT NULL CHECK (status IN ('ISSUED', 'RETURNED')),
    condition_on_issue    VARCHAR2(100) DEFAULT 'GOOD',
    condition_on_return   VARCHAR2(100),
    remarks               VARCHAR2(300),
    return_remarks        VARCHAR2(300),
    issued_by             VARCHAR2(50) NOT NULL,
    returned_to           VARCHAR2(50),
    created_at            TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at            TIMESTAMP,
    CONSTRAINT chk_issue_dates CHECK (expected_return_date IS NULL OR expected_return_date >= issue_date)
);

-- 3. Indexes for queries, search, and foreign keys
CREATE INDEX idx_issues_asset ON asset_issues(asset_id);
CREATE INDEX idx_issues_user ON asset_issues(issued_to_user_id);
CREATE INDEX idx_issues_dept ON asset_issues(issued_to_department);
CREATE INDEX idx_issues_status ON asset_issues(status);

COMMIT;
EXIT;
