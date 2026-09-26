-- Module 9: User Management Schema Evolution
-- Adds ACTIVE (for soft-delete deactivation) and MUST_CHANGE_PASSWORD (for temp password mitigation)

ALTER TABLE USERS ADD (
    ACTIVE VARCHAR2(1) DEFAULT 'Y' NOT NULL CHECK (ACTIVE IN ('Y', 'N')),
    MUST_CHANGE_PASSWORD VARCHAR2(1) DEFAULT 'N' NOT NULL CHECK (MUST_CHANGE_PASSWORD IN ('Y', 'N'))
);

COMMIT;
