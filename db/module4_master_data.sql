-- ==============================================================================
-- CAMS - Campus Asset Management System
-- Module 4: Master Data (Departments, Locations, Categories) Schema & Migration
-- Database: Oracle Database (xepdb1)
-- ==============================================================================

SET DEFINE OFF;

-- 1. Drop existing FK constraints on ASSETS if present
BEGIN
    EXECUTE IMMEDIATE 'ALTER TABLE ASSETS DROP CONSTRAINT FK_ASSETS_DEPARTMENT';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -2443 AND SQLCODE != -942 THEN NULL; END IF;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'ALTER TABLE ASSETS DROP CONSTRAINT FK_ASSETS_LOCATION';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -2443 AND SQLCODE != -942 THEN NULL; END IF;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'ALTER TABLE ASSETS DROP CONSTRAINT FK_ASSETS_CATEGORY';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -2443 AND SQLCODE != -942 THEN NULL; END IF;
END;
/

-- 2. Create DEPARTMENTS table
BEGIN
    EXECUTE IMMEDIATE 'DROP TABLE DEPARTMENTS CASCADE CONSTRAINTS';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -942 THEN NULL; END IF;
END;
/

CREATE TABLE departments (
    department_id    VARCHAR2(50) PRIMARY KEY,
    department_name  VARCHAR2(150) NOT NULL,
    active           CHAR(1) DEFAULT 'Y' NOT NULL CHECK (active IN ('Y', 'N')),
    created_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at       TIMESTAMP
);

-- 3. Create LOCATIONS table
BEGIN
    EXECUTE IMMEDIATE 'DROP TABLE LOCATIONS CASCADE CONSTRAINTS';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -942 THEN NULL; END IF;
END;
/

CREATE TABLE locations (
    location_id      VARCHAR2(50) PRIMARY KEY,
    location_name    VARCHAR2(150) NOT NULL,
    active           CHAR(1) DEFAULT 'Y' NOT NULL CHECK (active IN ('Y', 'N')),
    created_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at       TIMESTAMP
);

-- 4. Create CATEGORIES table
BEGIN
    EXECUTE IMMEDIATE 'DROP TABLE CATEGORIES CASCADE CONSTRAINTS';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -942 THEN NULL; END IF;
END;
/

CREATE TABLE categories (
    category_id      VARCHAR2(50) PRIMARY KEY,
    category_name    VARCHAR2(150) NOT NULL,
    active           CHAR(1) DEFAULT 'Y' NOT NULL CHECK (active IN ('Y', 'N')),
    created_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at       TIMESTAMP
);

-- 5. Seed initial distinct data for DEPARTMENTS
INSERT INTO departments (department_id, department_name, active) VALUES ('CSE', 'Computer Science and Engineering', 'Y');
INSERT INTO departments (department_id, department_name, active) VALUES ('IT', 'Information Technology', 'Y');
INSERT INTO departments (department_id, department_name, active) VALUES ('ECE', 'Electronics and Communication Engineering', 'Y');
INSERT INTO departments (department_id, department_name, active) VALUES ('EEE', 'Electrical and Electronics Engineering', 'Y');
INSERT INTO departments (department_id, department_name, active) VALUES ('MECH', 'Mechanical Engineering', 'Y');
INSERT INTO departments (department_id, department_name, active) VALUES ('CIVIL', 'Civil Engineering', 'Y');

-- Also seed any distinct departments already in ASSETS not captured above
MERGE INTO departments d
USING (SELECT DISTINCT department AS dept_id FROM assets WHERE department IS NOT NULL) a
ON (d.department_id = a.dept_id)
WHEN NOT MATCHED THEN
  INSERT (department_id, department_name, active) VALUES (a.dept_id, a.dept_id, 'Y');

-- 6. Seed initial distinct data for LOCATIONS
INSERT INTO locations (location_id, location_name, active) VALUES ('CSE Lab 1', 'CSE Lab 1', 'Y');
INSERT INTO locations (location_id, location_name, active) VALUES ('IT Lab 1', 'IT Lab 1', 'Y');
INSERT INTO locations (location_id, location_name, active) VALUES ('ECE Lab 1', 'ECE Lab 1', 'Y');
INSERT INTO locations (location_id, location_name, active) VALUES ('Room 101', 'Room 101', 'Y');

MERGE INTO locations l
USING (SELECT DISTINCT location AS loc_id FROM assets WHERE location IS NOT NULL) a
ON (l.location_id = a.loc_id)
WHEN NOT MATCHED THEN
  INSERT (location_id, location_name, active) VALUES (a.loc_id, a.loc_id, 'Y');

-- 7. Seed initial distinct data for CATEGORIES
INSERT INTO categories (category_id, category_name, active) VALUES ('Computer', 'Computer', 'Y');
INSERT INTO categories (category_id, category_name, active) VALUES ('Laboratory Equipment', 'Laboratory Equipment', 'Y');
INSERT INTO categories (category_id, category_name, active) VALUES ('Classroom Asset', 'Classroom Asset', 'Y');
INSERT INTO categories (category_id, category_name, active) VALUES ('Furniture', 'Furniture', 'Y');
INSERT INTO categories (category_id, category_name, active) VALUES ('Other', 'Other', 'Y');

MERGE INTO categories c
USING (SELECT DISTINCT category AS cat_id FROM assets WHERE category IS NOT NULL) a
ON (c.category_id = a.cat_id)
WHEN NOT MATCHED THEN
  INSERT (category_id, category_name, active) VALUES (a.cat_id, a.cat_id, 'Y');

COMMIT;

-- 8. Alter ASSETS table to enforce foreign keys against lookup tables
ALTER TABLE ASSETS ADD CONSTRAINT fk_assets_department FOREIGN KEY (department) REFERENCES departments(department_id);
ALTER TABLE ASSETS ADD CONSTRAINT fk_assets_location FOREIGN KEY (location) REFERENCES locations(location_id);
ALTER TABLE ASSETS ADD CONSTRAINT fk_assets_category FOREIGN KEY (category) REFERENCES categories(category_id);

COMMIT;
EXIT;
