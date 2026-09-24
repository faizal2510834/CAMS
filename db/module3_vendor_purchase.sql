-- ==============================================================================
-- CAMS - Campus Asset Management System
-- Module 3: Vendor & Purchase Management Schema & Migration
-- Database: Oracle Database (xepdb1)
-- ==============================================================================

SET DEFINE OFF;

-- 1. Drop existing FK from ASSETS if present
BEGIN
    EXECUTE IMMEDIATE 'ALTER TABLE ASSETS DROP CONSTRAINT FK_ASSETS_VENDOR';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -2443 AND SQLCODE != -942 THEN
            NULL;
        END IF;
END;
/

-- 2. Drop PURCHASES and VENDORS tables idempotently
BEGIN
    EXECUTE IMMEDIATE 'DROP TABLE PURCHASES CASCADE CONSTRAINTS';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -942 THEN NULL; END IF;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'DROP TABLE VENDORS CASCADE CONSTRAINTS';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -942 THEN NULL; END IF;
END;
/

-- 3. Create VENDORS table per Module 3 specification
CREATE TABLE vendors (
  vendor_id    VARCHAR2(50) PRIMARY KEY,
  vendor_name  VARCHAR2(150) NOT NULL,
  contact      VARCHAR2(20),
  email        VARCHAR2(150),
  address      VARCHAR2(300),
  active       CHAR(1) DEFAULT 'Y' CHECK (active IN ('Y','N')),
  created_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at   TIMESTAMP
);

-- 4. Create PURCHASES table per Module 3 specification
CREATE TABLE purchases (
  purchase_id     VARCHAR2(50) PRIMARY KEY,
  asset_id        VARCHAR2(50) UNIQUE NOT NULL REFERENCES assets(asset_id),
  vendor_id       VARCHAR2(50) NOT NULL REFERENCES vendors(vendor_id),
  invoice_number  VARCHAR2(100) NOT NULL,
  purchase_date   DATE NOT NULL,
  cost            NUMBER(12,2) NOT NULL CHECK (cost > 0),
  status          VARCHAR2(20) DEFAULT 'PENDING' CHECK (status IN ('PENDING','APPROVED','REJECTED')),
  approved_by     VARCHAR2(50),
  approved_at     TIMESTAMP,
  created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 5. Migration: Null out free-text VENDOR_ID on ASSETS and add FK constraint
UPDATE assets SET vendor_id = NULL;
COMMIT;

ALTER TABLE assets ADD CONSTRAINT fk_assets_vendor FOREIGN KEY (vendor_id) REFERENCES vendors(vendor_id);

-- Helpful functional indexes
CREATE INDEX idx_vendors_name ON vendors (LOWER(vendor_name));
CREATE INDEX idx_vendors_active ON vendors (active);
CREATE INDEX idx_purchases_status ON purchases (status);
CREATE INDEX idx_purchases_vendor ON purchases (vendor_id);

-- 6. Initial Seed Data: Vendors
INSERT INTO vendors (vendor_id, vendor_name, contact, email, address, active, created_at)
VALUES ('VND-DELL-01', 'Dell Technologies India Pvt Ltd', '+91-80-67890000', 'enterprisesales@dell.co.in', 'Dell Campus, Outer Ring Road, Bangalore - 560037', 'Y', CURRENT_TIMESTAMP);

INSERT INTO vendors (vendor_id, vendor_name, contact, email, address, active, created_at)
VALUES ('VND-CISCO-02', 'Cisco Systems India Pvt Ltd', '+91-80-44260000', 'india-orders@cisco.com', 'SEZ Cessna Business Park, Kadubeesanahalli, Bangalore - 560103', 'Y', CURRENT_TIMESTAMP);

INSERT INTO vendors (vendor_id, vendor_name, contact, email, address, active, created_at)
VALUES ('VND-EPSON-03', 'Epson India Pvt Ltd', '+91-80-45665000', 'commercial@epson.co.in', 'Millenia Tower A, Murphy Road, Ulsoor, Bangalore - 560008', 'Y', CURRENT_TIMESTAMP);

INSERT INTO vendors (vendor_id, vendor_name, contact, email, address, active, created_at)
VALUES ('VND-APPLE-04', 'Apple India Private Limited', '+91-80-40455000', 'campus_support@apple.com', '19th Floor, Concorde Tower C, UB City, Bangalore - 560001', 'Y', CURRENT_TIMESTAMP);

INSERT INTO vendors (vendor_id, vendor_name, contact, email, address, active, created_at)
VALUES ('VND-DIS-09', 'Old Legacy Discontinued Tech', '+91-11-23456789', 'archived@legacytech.in', 'Nehru Place, New Delhi - 110019', 'N', CURRENT_TIMESTAMP);

INSERT INTO vendors (vendor_id, vendor_name, contact, email, address, active, created_at)
VALUES ('VND-TEST-01', 'Test Equipment Supplier', '+91-9876543210', 'test@supplier.com', 'Test City', 'Y', CURRENT_TIMESTAMP);

INSERT INTO vendors (vendor_id, vendor_name, contact, email, address, active, created_at)
VALUES ('VND-DELL', 'Dell Direct Partner Supplies', '+91-9876543211', 'delldirect@partner.com', 'Bangalore', 'Y', CURRENT_TIMESTAMP);

COMMIT;

EXIT;
