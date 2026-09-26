# CAMS — Campus Asset Management System

A multi-tiered web application designed for higher education institutions to track, allocate, maintain, and audit academic and IT equipment across university departments and labs.

Built with **Java 17**, **Jakarta Servlet 6.0**, **Oracle Database (xepdb1)**, and **Vanilla Modern CSS/ES6 JavaScript**.

---

## 🏛️ System Architecture

CAMS strictly enforces a clean multi-tier architectural pipeline without external ORM or framework overhead:

```
[Browser Client (HTML5 / Vanilla CSS / ES6 JavaScript)]
                         │
                         ▼
[AuthenticationFilter] ──(Active HttpSession? Yes ──> Next / No ──> 401 JSON or 302 Redirect)
                         │
                         ▼
[AuthorizationFilter]  ──(Role authorized for URI/Method? Yes ──> Next / No ──> 403 Forbidden)
                         │
                         ▼
[Controllers (Servlets)] ── (AuthServlet, AssetServlet, PingServlet, RoleTestServlet)
                         │
                         ▼
[Service Layer] ──────── (AuthService, AssetService, PingService, PasswordUtil)
                         │
                         ▼
[Data Access Objects] ── (UserDAO, AssetDAO, PingDAO)
                         │
                         ▼
[JDBC Connection Pool] ─ (DBConnection via ojdbc11.jar)
                         │
                         ▼
[Oracle Database 23c/21c/19c (USERS, ASSETS tables)]
```

---

## 🚀 Module Completion Status

### ✅ Module 0: Infrastructure & Round-Trip Health Verification
- **Round-Trip Ping**: Verifies connectivity end-to-end across all 6 layers from browser to Oracle DB.
- **Diagnostic Dashboard**: Interactive pipeline visualizer testing latency, database metadata, and PreparedStatement bindings at `/index.html`.
- **JDBC Connection Manager**: Centralized singleton connection pooling via [`DBConnection.java`](src/main/java/com/cams/util/DBConnection.java).

### ✅ Module 1: Authentication & Role-Based Access Control (RBAC)
- **Role Hierarchy**:
  - `Administrator`: Full system authorization, asset CRUD, user control.
  - `Faculty`: Academic self-service, asset requisitions, read-only asset search.
  - `Technical Staff`: Maintenance workspace, repair queues, read-only asset search.
- **Security & Password Hashing**: Native `PBKDF2WithHmacSHA256` implementation in [`PasswordUtil.java`](src/main/java/com/cams/util/PasswordUtil.java) using 65,536 iterations and cryptographically secure random salts. Passwords are never stored in plain text.
- **Case-Insensitive Usernames**: All usernames are explicitly normalized to lowercase prior to database storage and lookup.
- **Session Management**: Session fixation defense on login, 20-minute inactivity session timeout configured in [`web.xml`](src/main/webapp/WEB-INF/web.xml) per SRS requirements.
- **Servlet Filters**: Real filter-level interception ([`AuthenticationFilter`](src/main/java/com/cams/filter/AuthenticationFilter.java) and [`AuthorizationFilter`](src/main/java/com/cams/filter/AuthorizationFilter.java)).
- **UI Dashboards**:
  - [`/pages/login.html`](src/main/webapp/pages/login.html): Modern dark-glass login with quick-fill presets for demo roles.
  - [`/pages/admin/dashboard.html`](src/main/webapp/pages/admin/dashboard.html): Administrator Control Hub.
  - [`/pages/faculty/dashboard.html`](src/main/webapp/pages/faculty/dashboard.html): Faculty Self-Service Portal.
  - [`/pages/technical/dashboard.html`](src/main/webapp/pages/technical/dashboard.html): Technical Maintenance Workspace.
  - [`/pages/access-denied.html`](src/main/webapp/pages/access-denied.html): 403 Forbidden intercept view.

### ✅ Module 2A: Asset Management Core Registry
- **Schema & DDL**: Self-contained script in [`db/module2_assets.sql`](db/module2_assets.sql). Zero runtime DDL executed on server startup.
- **Single Status Model**: `STATUS` column strictly bounded to `AVAILABLE`, `ISSUED`, `UNDER_MAINTENANCE`, `DISPOSED`.
- **Soft-Delete / Retirement**: Triggering "Retire" transitions status to `DISPOSED` and records `DISPOSED_DATE` and `DISPOSAL_REASON`. Hard `DELETE FROM ASSETS` is completely prohibited.
- **Allowed Lifecycle State Transitions**:
  - `AVAILABLE` $\rightarrow$ `ISSUED`, `UNDER_MAINTENANCE`, `DISPOSED`
  - `ISSUED` $\rightarrow$ `AVAILABLE`, `UNDER_MAINTENANCE`
  - `UNDER_MAINTENANCE` $\rightarrow$ `AVAILABLE`, `DISPOSED`
  - `DISPOSED` is terminal.
- **Business Rule Validations**:
  - `ASSET_ID` format verified against regex `^[A-Z0-9-]+$`.
  - Duplicate `ASSET_ID` rejected at both the Service layer and Database constraint level (HTTP 409).
  - `PURCHASE_COST` must be $> 0$ (values $\le 0$ rejected with HTTP 400).
  - `WARRANTY_EXPIRY` must be $\ge$ `PURCHASE_DATE`.
  - Master dropdown options fed from [`AssetConstants.java`](src/main/java/com/cams/util/AssetConstants.java) (`CSE`, `IT`, `ECE`, `EEE`, `MECH`, `CIVIL` departments; `Computer`, `Laboratory Equipment`, `Classroom Asset`, `Furniture`, `Other` categories; fixed locations). Free-text entry is strictly blocked.
  - Retiring an `ISSUED` or already-`DISPOSED` asset returns HTTP 409 Conflict.
  - Editing a `DISPOSED` asset returns HTTP 409 Conflict.
  - `asset_id` and `status` are immutable via PUT update.
  - `changeStatus(Connection con, String assetId, String expectedStatus, String newStatus)` provides optimistic concurrency locking.
- **Pagination & Whitelisted Sorting**:
  - `GET /api/assets?page=1&size=10` uses Oracle `OFFSET ? ROWS FETCH NEXT ? ROWS ONLY`.
  - Dynamic filtering by keyword, department, category, operational status, and retired inclusion toggle.

### ✅ Module 2B: Category-Specific Asset Details & Atomic Transactions
- **Schema & DDL**: Dedicated 1:1 foreign-keyed detail tables in [`db/module2b_details.sql`](db/module2b_details.sql):
  - `COMPUTER_DETAILS`: `CPU`, `MONITOR`, `KEYBOARD`, `MOUSE`, `PRINTER`, `SOFTWARE`, `IP_ADDRESS`.
  - `CLASSROOM_DETAILS`: `FURNITURE_DESC`, `PROJECTOR`, `SMART_BOARD`, `AC`, `SEATING_CAPACITY`.
  - `LAB_DETAILS`: `EQUIPMENT_TYPE`, `EQUIPMENT_CONDITION`, `LAST_CALIBRATION_DATE`.
  - `FURNITURE_DETAILS`: `FURNITURE_TYPE`, `MATERIAL`, `QUANTITY`.
  - Keyed 1:1 on `ASSET_ID` referencing `ASSETS(ASSET_ID) ON DELETE CASCADE`. Existing `ASSETS` table structure is untouched.
- **Transactional DAO Refactoring**:
  - `AssetDAO`, `ComputerDetailsDAO`, `ClassroomDetailsDAO`, `LabDetailsDAO`, and `FurnitureDetailsDAO` accept external `Connection` arguments.
  - `insertOrUpdate(Connection conn, String assetId, T details)` and `findByAssetId(Connection conn, String assetId)`.
- **Atomic Transaction Service Layer**:
  - `AssetService.createAsset()` and `updateAsset()` manage explicit transactions (`setAutoCommit(false)`, persist asset, persist matching detail row, `commit()`).
  - Exceptions trigger `rollback()` leaving **zero orphaned asset records** in the database.
- **Strict Payload Shape & Field Validations**:
  - Details payload shape must strictly match declared category; mismatch rejected with HTTP 400.
  - `ip_address` validated as IPv4 format if present.
  - `seating_capacity` and `quantity` must be positive integers ($> 0$).
  - `last_calibration_date` parsed as valid SQL date.
  - Category `Other` skips detail tables entirely (`details: null`).
- **Endpoints**:
  - `POST /api/assets` & `PUT /api/assets/{id}` accept optional nested `details` object.
  - `GET /api/assets/{id}` returns nested `details` (or `null` if none recorded).
  - `GET /api/assets` (list) remains fast without detail joins.
- **Dynamic Frontend UI**:
  - In [`/pages/assets.html`](src/main/webapp/pages/assets.html) & [`/js/assets.js`](src/main/webapp/js/assets.js), changing Category dynamically reveals matching detail specification inputs.
  - View Details modal renders full category specifications read-only.
- **Backward Compatibility**:
  - Pre-Module-2B assets return `details: null` cleanly without error.
- **Automated Verification**:
  - Complete 9-point test suite in [`test_module2b.ps1`](test_module2b.ps1).

---

## 📋 Default Seed Credentials

| Role | Username | Password | Default Dashboard |
| :--- | :--- | :--- | :--- |
| **Administrator** | `admin` | `Admin@123` | `/pages/admin/dashboard.html` |
| **Faculty** | `faculty1` | `Faculty@123` | `/pages/faculty/dashboard.html` |
| **Technical Staff** | `tech1` | `Tech@123` | `/pages/technical/dashboard.html` |

---

## 📡 REST API Reference

### Authentication (`/api/auth/*`)
- `POST /api/auth/login` — Authenticate credentials, start new `HttpSession`, return profile & redirect URL.
- `POST /api/auth/logout` — Invalidate session and clear security context.
- `GET /api/auth/session` — Retrieve active authenticated session info.

### Asset Management (`/api/assets/*`)
- `GET /api/assets/options` — Master dropdown lists (Departments, Categories, Locations, Statuses).
- `GET /api/assets` — Paged asset search and filtering.
- `GET /api/assets/{id}` — Retrieve detailed asset specifications.
- `POST /api/assets` — Register new asset (Administrator only).
- `PUT /api/assets/{id}` — Update asset metadata and location (Administrator only).
- `PUT /api/assets/{id}/retire` — Soft-delete asset with disposal reason (Administrator only).

### Diagnostics & RBAC Verification
- `GET /api/ping` — Infrastructure health roundtrip check.
- `GET /api/admin/summary` — Administrator RBAC verification endpoint.
- `GET /api/faculty/summary` — Faculty RBAC verification endpoint.
- `GET /api/technical/summary` — Technical Staff RBAC verification endpoint.

---

## 🛠️ Setup & Running Instructions

### Prerequisites
- **JDK 17** or later.
- **Oracle Database 19c / 21c / 23c XE** running locally on port `1521` with pluggable database `xepdb1`.

### 1. Database Configuration
Edit [`src/main/resources/db.properties`](src/main/resources/db.properties):
```properties
db.driver=oracle.jdbc.OracleDriver
db.url=jdbc:oracle:thin:@localhost:1521/xepdb1
db.username=system
db.password=YOUR_ORACLE_PASSWORD
```

### 2. Database DDL & Seed Execution
Execute the Module 2 SQL script in Oracle SQL*Plus or SQL Developer as user `CAMS` (or your configured DB user):
```sql
@db/module2_assets.sql
```

### 3. Build & Run Local Dev Server
Run the embedded Apache Tomcat server:
```powershell
.\mvnw.cmd exec:java
```
Or use the launcher scripts:
```powershell
.\run.ps1
```
The server will start on **`http://localhost:8080`**.

### 4. Run Automated Test Suites
Run the automated end-to-end PowerShell verification suites:
```powershell
powershell -ExecutionPolicy Bypass -File test_module2.ps1
powershell -ExecutionPolicy Bypass -File test_module2b.ps1
powershell -ExecutionPolicy Bypass -File test_module3.ps1
```

---

## 🏢 Module 3: Vendor & Purchase Management

Module 3 adds complete supplier lifecycle and procurement management with atomic transactional approvals:

### 1. Database Schema & Migration (`db/module3_vendor_purchase.sql`)
- **`VENDORS` Table**: `vendor_id` (PK), `vendor_name`, `contact`, `email`, `address`, `active` ('Y'/'N'), `created_at`, `updated_at`.
- **`PURCHASES` Table**: `purchase_id` (PK), `asset_id` (UNIQUE FK referencing `ASSETS`), `vendor_id` (FK referencing `VENDORS`), `invoice_number`, `purchase_date`, `cost` (> 0), `status` ('PENDING','APPROVED','REJECTED'), `approved_by`, `approved_at`, `created_at`.
- **Migration**: Nulls out legacy free-text `ASSETS.VENDOR_ID` values and adds FK constraint `fk_assets_vendor` referencing `VENDORS(VENDOR_ID)`.

### 2. Business Rules & Atomic Transactions
- **Vendor Validation**: Name required; proper email format validation; numeric-ish contact validation; soft deactivation (`active = 'N'`) preventing new purchases while preserving audit history.
- **Purchase Constraints**:
  - `asset_id` must reference an existing asset with no prior purchase (1:1 relationship, clean 409 Conflict).
  - `vendor_id` must reference an active vendor (400 if inactive).
  - `cost > 0` and `purchase_date` cannot be in the future (400 Bad Request).
  - New purchases start as `PENDING`.
- **Atomic Approval (`PUT /api/purchases/{id}/approve`)**:
  - Managed inside a single database transaction (`setAutoCommit(false)`):
    1. Sets purchase status to `APPROVED`, sets `approved_by` to session user and `approved_at` to current timestamp.
    2. Atomically sets `ASSETS.VENDOR_ID` on the linked asset to the purchase's `vendor_id` and refreshes `ASSETS.UPDATED_AT`.
    3. Both writes commit together or rollback together.
- **Rejection (`PUT /api/purchases/{id}/reject`)**:
  - Sets purchase status to `REJECTED` with optional reason; asset's vendor remains untouched.
  - Approving or rejecting an already-approved or already-rejected purchase returns 409 Conflict.
- **Role-Based Access Control**:
  - All `/api/vendors/*` and `/api/purchases/*` endpoints are Administrator-only. Faculty and Technical Staff receive HTTP 403 Forbidden.

### 3. REST API Endpoints
| Method | Endpoint | Allowed Roles | Description |
|---|---|---|---|
| `GET` | `/api/vendors?search=&active=&page=&size=` | Administrator | List vendors with search & status filter |
| `GET` | `/api/vendors/{id}` | Administrator | Get vendor by ID |
| `POST` | `/api/vendors` | Administrator | Register new supplier |
| `PUT` | `/api/vendors/{id}` | Administrator | Update vendor details |
| `PUT` | `/api/vendors/{id}/deactivate` | Administrator | Deactivate vendor (sets `active='N'`) |
| `GET` | `/api/purchases?status=&vendorId=&page=&size=` | Administrator | List purchases with status & vendor filter |
| `GET` | `/api/purchases/{id}` | Administrator | Get procurement order details |
| `POST` | `/api/purchases` | Administrator | Record procurement against unpurchased asset |
| `PUT` | `/api/purchases/{id}/approve` | Administrator | Atomically approve purchase & link vendor to asset |
| `PUT` | `/api/purchases/{id}/reject` | Administrator | Reject purchase order |

---

### ✅ Module 4: Master Data Management (Departments, Locations, Categories)
- **Lookup Normalization**:
  - `DEPARTMENTS`: `department_id` (PK, natural code e.g. `'CSE'`), `department_name`, `active` ('Y'/'N').
  - `LOCATIONS`: `location_id` (PK, natural code/name e.g. `'CSE Lab 1'`), `location_name`, `building`, `floor_number`, `active`.
  - `CATEGORIES`: `category_id` (PK, natural code/name e.g. `'Computer'`), `category_name`, `description`, `active`.
  - Note: Natural-key primary keys were selected for direct non-destructive migration against existing `ASSETS` columns. Renaming a code later would require cascading updates.
- **Non-Destructive Migration**:
  - [`db/module4_master_data.sql`](db/module4_master_data.sql) extracted existing distinct values from `ASSETS`, populated lookup tables, and attached foreign keys `fk_assets_department`, `fk_assets_location`, `fk_assets_category` without any data loss.
- **Dynamic Asset Validation**:
  - `AssetServiceImpl` queries `MasterDataService` to ensure newly registered or edited assets only reference active departments, locations, and categories.
  - `/api/assets/options` dynamically aggregates active entries directly from master lookup tables.
- **Administrator CRUD Endpoints**:
  - `/api/departments`, `/api/locations`, `/api/categories` with POST (create), PUT (update), PUT `/deactivate` (soft-deactivate).

---

### ✅ Module 5: Equipment Issue & Return Management
- **Schema & DDL**:
  - [`db/module5_issue_return.sql`](db/module5_issue_return.sql): `ASSET_ISSUES` tracking `issue_id` (PK), `asset_id` (FK to `ASSETS`), `issued_to_user_id` (FK to `USERS`), `issued_to_department` (FK to `DEPARTMENTS`), `issue_date`, `expected_return_date`, `actual_return_date`, `status` ('ISSUED', 'RETURNED'), `condition_on_issue`, `condition_on_return`, `remarks`, `return_remarks`, `issued_by`, `returned_to`.
- **Department Source Decoupling**:
  - `USERS.DEPARTMENT` stores descriptive text and is not altered. Issue forms provide a dropdown populated from `DEPARTMENTS`, and the backend strictly validates foreign key membership.
- **Atomic Concurrency Protection**:
  - **Issue Race**: `assetDAO.changeStatus(con, assetId, "AVAILABLE", "ISSUED")` checks affected rows == 1. In simultaneous race conditions, exactly 1 request succeeds and the other receives a clean HTTP 409 Conflict.
  - **Return Race**: `UPDATE asset_issues SET actual_return_date = ? ... WHERE issue_id = ? AND actual_return_date IS NULL AND status = 'ISSUED'` checks affected rows == 1. Concurrent duplicate returns return HTTP 409 Conflict.
- **Condition-Based Status Branching**:
  - Returning in `GOOD` condition automatically reverts asset status to `AVAILABLE`.
  - Returning in `DAMAGED` condition automatically routes asset status to `UNDER_MAINTENANCE` for technical repair queues.
- **Transaction Rollback Protection**:
  - If `ASSET_ISSUES` insert fails after status transition, explicit database `rollback()` reverts asset status back to `AVAILABLE`.
- **Role-Based Access & Ownership Enforcement**:
  - **Faculty**: Self-service issue to self, return own loans. Strictly blocked (HTTP 403) from accessing or returning transactions belonging to other faculty.
  - **Administrator**: Full system visibility and assisted issue/return workflows.
  - **Technical Staff**: Read-only inventory and audit access. Strictly blocked (HTTP 403) on POST and PUT.

---

### ✅ Module 6: Maintenance Management & Repair Queues
- **Schema & DDL**:
  - [`db/module6_maintenance.sql`](db/module6_maintenance.sql): `MAINTENANCE` tracking `maintenance_id` (PK, `'MNT-...'`), `asset_id` (FK to `ASSETS`), `scheduled_date`, `completed_date`, `fault_description`, `technician`, `cost` (CHECK >= 0), `status` ('SCHEDULED', 'IN_PROGRESS', 'COMPLETED', 'REQUIRES_FURTHER_REPAIR'), `created_by`, `issue_id` (FK to `ASSET_ISSUES`), `created_at`, `updated_at`.
- **Damaged Return Auto-Linkage**:
  - When equipment is returned in `DAMAGED` condition in Module 5, the system automatically provisions an active maintenance ticket in `SCHEDULED` status referencing the origin `issue_id`, preserving cross-module accountability.
- **Preventative & Corrective Scheduling**:
  - Technical Staff and Administrators can schedule preventative work orders on available equipment, transitioning the asset to `UNDER_MAINTENANCE`.
  - Duplicate active maintenance tickets on the same asset are rejected with HTTP 409 Conflict.
  - Scheduling maintenance on an `ISSUED` asset is blocked with HTTP 409 Conflict (must be returned first).
- **Status Workflow & Availability Restoration**:
  - Status progression: `SCHEDULED` $\rightarrow$ `IN_PROGRESS` $\rightarrow$ `COMPLETED` / `REQUIRES_FURTHER_REPAIR`.
  - Marking a ticket `COMPLETED` atomically populates `completed_date`, logs final repair cost, and restores the asset status back to `AVAILABLE`.
  - Completed tickets enter an immutable terminal state (subsequent modifications rejected with HTTP 409 Conflict).
- **Workspace UI**:
  - [`/pages/technical/maintenance.html`](src/main/webapp/pages/technical/maintenance.html): Interactive repair queue with modal actions for scheduling, progress updates, cost logging, and completion.

---

### ✅ Module 7: Asset Depreciation & Institutional Valuation (Admin-Only)
- **Zero New Tables Architecture**:
  - Calculates straight-line depreciation entirely on-the-fly from `ASSETS.PURCHASE_DATE` and `ASSETS.PURCHASE_COST` without creating redundant transaction tables.
- **Straight-Line Depreciation Mathematical Model**:
  - Useful life defaults by category: Computer (4 yrs), Classroom Asset (7 yrs), Laboratory Equipment (5 yrs), Furniture (10 yrs), Other (5 yrs).
  - Annual Depreciation: $\text{Cost} / \text{Useful Life}$.
  - Elapsed Time: $\text{Days between Purchase Date and Today} / 365.25$.
  - Current Book Value: $\max(0.00, \text{Cost} - (\text{Annual Depreciation} \times \text{Elapsed Years}))$.
  - Accumulated Depreciation: $\text{Cost} - \text{Current Book Value}$.
- **Depreciation Schedule Breakdown**:
  - Dynamic schedule generation from Year 0 (procurement) through Year $N$ (useful life conclusion), showing start value, depreciation loss, and ending book value per annual period.
- **Campus-Wide Valuation Summary**:
  - Aggregated real-time metrics across all active campus equipment: Original Procurement Cost, Current Net Book Value, Accumulated Depreciation Loss, Active Valued Assets count, and Category breakdown schedule.
- **UI Schedule Inspector**:
  - Integrated directly into [`/pages/assets.html`](src/main/webapp/pages/assets.html) with an interactive modal inspecting any asset's straight-line financial schedule.

---

### ✅ Module 8: Physical Inventory Audits
- **Schema & DDL**:
  - [`db/module8_audit.sql`](db/module8_audit.sql): `INVENTORY_AUDITS` tracking `audit_id` (PK, `'AUD-...'`), `asset_id` (FK to `ASSETS`), `audit_date` (TIMESTAMP), `verified_by` (FK to `USERS(username)`), `status` ('VERIFIED', 'MISSING', 'MISLOCATED'), `remarks`, `created_at`.
- **Physical Verification Logging**:
  - Technical Staff record on-site physical audits for campus equipment, capturing location matches, missing hardware alerts, or misplaced assets.
- **Audit History & Search Engine**:
  - Query with multi-dimensional filtering by verification status (`VERIFIED`, `MISSING`, `MISLOCATED`), asset ID, department, and auditor.
- **Institutional Audit Metrics**:
  - Direct aggregation endpoint `GET /api/audits/summary` providing total verified vs. missing hardware counts.
- **Audit Workspace UI**:
  - [`/pages/technical/audit.html`](src/main/webapp/pages/technical/audit.html): Audit log with physical verification scanner modal and history filters.

---

### ✅ Module 9: User Account Administration & RBAC Security (Admin-Only)
- **Schema & DDL**:
  - [`db/module9_users.sql`](db/module9_users.sql): Added `MUST_CHANGE_PASSWORD VARCHAR2(1) DEFAULT 'N' NOT NULL CHECK (must_change_password IN ('Y', 'N'))` to `USERS`.
- **Administrative Account CRUD**:
  - Administrators can provision new Faculty, Technical Staff, and Administrator accounts with PBKDF2 password hashing. Passwords are never returned in responses.
  - Search and filter users by username, role, department, and active status.
- **Soft-Delete Deactivation Pattern**:
  - User accounts are soft-deleted by setting `ACTIVE = 'N'`. Hard `DELETE FROM USERS` is strictly prohibited to preserve foreign key history across Issue & Return, Maintenance, and Audit records.
  - Deactivated accounts are blocked at login with HTTP 401 Unauthorized.
  - Accounts can be reactivated seamlessly by Administrators.
- **Self-Lockout Prevention**:
  - An Administrator cannot deactivate their own account (rejected with HTTP 409 Conflict), preventing accidental lockout of the sole administrative account.
- **Secure Password Reset Flow with `must_change_password`**:
  - Administrators can reset credentials with a custom or auto-generated temporary password.
  - Automatically flags `must_change_password = 'Y'`. On next successful authentication, the login API returns `mustChangePassword: true`, requiring the user to immediately update their credentials.
- **User Management UI**:
  - [`/pages/admin/users.html`](src/main/webapp/pages/admin/users.html): User registry with role badges, status toggles, account creation modal, and password reset dialog.

---

### ✅ Module 10: Role-Based Live Dashboards & Enterprise Reports Center
- **Live Role Dashboards**:
  - **Administrator Dashboard** ([`/pages/admin/dashboard.html`](src/main/webapp/pages/admin/dashboard.html)): Real-time campus-wide KPIs (total, available, issued, maintenance, pending purchases, active repairs, overdue loans, users), live Module 7 valuation integration, and quick workspace hubs.
  - **Faculty Portal** ([`/pages/faculty/dashboard.html`](src/main/webapp/pages/faculty/dashboard.html)): Personal custody equipment counters, overdue alert counter (highlighted in red), and "My Active Equipment Loans" table with overdue badges and quick return actions.
  - **Technical Staff Workspace** ([`/pages/technical/dashboard.html`](src/main/webapp/pages/technical/dashboard.html)): Repair queue counts, total maintenance spend (₹), verified audit counts, and real-time operational feeds for Recent Maintenance Tickets and Recent Physical Audits.
- **Enterprise Reports Center (Admin-Only, SRS FR13)**:
  - [`/pages/admin/reports.html`](src/main/webapp/pages/admin/reports.html) & [`/js/reports.js`](src/main/webapp/js/reports.js): 7 core analytical sections:
    1. *Asset Lifecycle & Status Distribution*
    2. *Campus Valuation & Category Depreciation Summary*
    3. *Procurement & Acquisition History*
    4. *Equipment Circulation & Overdue Loans*
    5. *Maintenance & Hardware Servicing Operations*
    6. *Physical Inventory Audit Findings*
    7. *Departmental Asset Allocation & Valuation Breakdown*
  - Dynamic filtering by Department and Date Range.
  - **Print / PDF View**: High-contrast `@media print` stylesheet optimizing tables and cards for clean physical printing or browser PDF saving (`window.print()`).
  - **CSV Export**: Streamed via `GET /api/reports/export/csv`.
- **Zero-New-Tables Aggregation**:
  - Aggregates dynamically across existing tables (`ASSETS`, `ASSET_ISSUES`, `MAINTENANCE`, `PURCHASES`, `INVENTORY_AUDITS`, `USERS`, `DEPARTMENTS`) without creating redundant tables.
- **Verified Overdue Detection**:
  - Confirmed against `ASSET_ISSUES.EXPECTED_RETURN_DATE` column: actively detects and alerts on overdue loans in real time.

---

### 🎨 UI Consistency Standard
All functional pages follow the unified design standard established in the Asset Registry:
- Consistent top navigation bar with User Avatar, Name, Username, Department, Role Badge, and Sign Out action.
- Unified dark-glass theme (`#0f172a` backdrop, `#1e293b` cards with `rgba(255,255,255,0.06)` borders, subtle micro-animations).
- Responsive search and filter bars with standard button gradients.
- Standardized data tables with hover elevation, status pills, and empty-state placeholders.

---

## 📁 Repository Structure

```
CAMS/
├── pom.xml
├── README.md
├── run.bat / run.ps1
├── test_module2.ps1          # Modules 1 & 2 Core Test Suite
├── test_module2b.ps1         # Module 2B Categorized Details Test Suite
├── test_master_data.ps1      # Module 3 Master Data Registry Test Suite
├── test_issue_return.ps1     # Module 5 Equipment Issue & Return Test Suite
├── test_maintenance.ps1      # Module 6 Maintenance Management Test Suite
├── test_depreciation.ps1     # Module 7 Straight-Line Depreciation Test Suite
├── test_inventory_audit.ps1  # Module 8 Physical Inventory Audit Test Suite
├── test_user_management.ps1  # Module 9 User Administration Test Suite
├── test_dashboard_reports.ps1# Module 10 Dashboards & Reports Test Suite
├── db/
│   ├── module2_assets.sql
│   ├── module2b_details.sql
│   ├── module3_vendor_purchase.sql
│   ├── module4_master_data.sql
│   ├── module5_issue_return.sql
│   ├── module6_maintenance.sql
│   ├── module8_audit.sql
│   └── module9_users.sql
└── src/main/
    ├── java/com/cams/
    │   ├── Server.java
    │   ├── controller/
    │   │   ├── AssetServlet.java
    │   │   ├── AuditServlet.java
    │   │   ├── AuthServlet.java
    │   │   ├── CategoryServlet.java
    │   │   ├── DashboardServlet.java
    │   │   ├── DepartmentServlet.java
    │   │   ├── DepreciationServlet.java
    │   │   ├── IssueServlet.java
    │   │   ├── LocationServlet.java
    │   │   ├── MaintenanceServlet.java
    │   │   ├── PingServlet.java
    │   │   ├── PurchaseServlet.java
    │   │   ├── ReportServlet.java
    │   │   ├── RoleTestServlet.java
    │   │   ├── UserServlet.java
    │   │   └── VendorServlet.java
    │   ├── dao/
    │   │   ├── AssetDAO.java / AssetDAOImpl.java
    │   │   ├── AuditDAO.java / AuditDAOImpl.java
    │   │   ├── CategoryDAO.java / CategoryDAOImpl.java
    │   │   ├── ClassroomDetailsDAO.java / ClassroomDetailsDAOImpl.java
    │   │   ├── ComputerDetailsDAO.java / ComputerDetailsDAOImpl.java
    │   │   ├── DepartmentDAO.java / DepartmentDAOImpl.java
    │   │   ├── FurnitureDetailsDAO.java / FurnitureDetailsDAOImpl.java
    │   │   ├── IssueDAO.java / IssueDAOImpl.java
    │   │   ├── LabDetailsDAO.java / LabDetailsDAOImpl.java
    │   │   ├── LocationDAO.java / LocationDAOImpl.java
    │   │   ├── MaintenanceDAO.java / MaintenanceDAOImpl.java
    │   │   ├── PingDAO.java / PingDAOImpl.java
    │   │   ├── PurchaseDAO.java / PurchaseDAOImpl.java
    │   │   ├── UserDAO.java / UserDAOImpl.java
    │   │   └── VendorDAO.java / VendorDAOImpl.java
    │   ├── filter/
    │   │   ├── AuthenticationFilter.java
    │   │   └── AuthorizationFilter.java
    │   ├── model/
    │   │   ├── Asset.java
    │   │   ├── AssetDepreciation.java
    │   │   ├── AssetIssue.java
    │   │   ├── AssetQueryCriteria.java
    │   │   ├── AuditQueryCriteria.java
    │   │   ├── AuditRecord.java
    │   │   ├── AuditSummary.java
    │   │   ├── Category.java
    │   │   ├── ClassroomDetails.java
    │   │   ├── ComputerDetails.java
    │   │   ├── Department.java
    │   │   ├── DepreciationCategorySummary.java
    │   │   ├── DepreciationScheduleItem.java
    │   │   ├── DepreciationSummary.java
    │   │   ├── FurnitureDetails.java
    │   │   ├── LabDetails.java
    │   │   ├── Location.java
    │   │   ├── Maintenance.java
    │   │   ├── PagedResult.java
    │   │   ├── PingResult.java
    │   │   ├── Purchase.java
    │   │   ├── User.java
    │   │   └── Vendor.java
    │   ├── service/
    │   │   ├── AssetService.java / AssetServiceImpl.java
    │   │   ├── AuditService.java / AuditServiceImpl.java
    │   │   ├── AuthService.java / AuthServiceImpl.java
    │   │   ├── DashboardService.java / DashboardServiceImpl.java
    │   │   ├── DepreciationService.java / DepreciationServiceImpl.java
    │   │   ├── IssueService.java / IssueServiceImpl.java
    │   │   ├── MaintenanceService.java / MaintenanceServiceImpl.java
    │   │   ├── MasterDataService.java / MasterDataServiceImpl.java
    │   │   ├── PingService.java
    │   │   ├── PurchaseService.java / PurchaseServiceImpl.java
    │   │   ├── ReportService.java / ReportServiceImpl.java
    │   │   ├── UserService.java / UserServiceImpl.java
    │   │   └── VendorService.java / VendorServiceImpl.java
    │   └── util/
    │       ├── AssetConstants.java
    │       ├── DBConnection.java
    │       ├── JsonUtil.java
    │       └── PasswordUtil.java
    ├── resources/
    │   ├── db.properties
    │   └── schema_module1.sql
    └── webapp/
        ├── index.html
        ├── css/style.css
        ├── js/
        │   ├── app.js
        │   ├── assets.js
        │   ├── audit.js
        │   ├── issues.js
        │   ├── maintenance.js
        │   ├── master-data.js
        │   ├── purchases.js
        │   ├── reports.js
        │   ├── users.js
        │   └── vendors.js
        ├── pages/
        │   ├── access-denied.html
        │   ├── assets.html
        │   ├── issues.html
        │   ├── login.html
        │   ├── master-data.html
        │   ├── purchases.html
        │   ├── vendors.html
        │   ├── admin/
        │   │   ├── dashboard.html
        │   │   ├── issues.html
        │   │   ├── master-data.html
        │   │   ├── purchases.html
        │   │   ├── reports.html
        │   │   ├── users.html
        │   │   └── vendors.html
        │   ├── faculty/dashboard.html
        │   └── technical/
        │       ├── audit.html
        │       ├── dashboard.html
        │       └── maintenance.html
        └── WEB-INF/web.xml
```

---

## 🧪 Automated Testing & Verification Matrix

The CAMS codebase is continuously tested against live Oracle XE 21c database instances using dedicated end-to-end PowerShell integration suites:

| Module | Test Suite File | Scope Covered | Passing Assertions | Status |
|---|---|---|---|---|
| **Modules 1 & 2** | `test_module2.ps1` | Authentication, RBAC 401/403, Single Status lifecycle, Soft-delete retirement | 13 | `100% PASS` |
| **Module 2B** | `test_module2b.ps1` | Categorized specifications, 1:1 detail tables, Atomic rollback | 9 | `100% PASS` |
| **Module 3** | `test_master_data.ps1` | Dynamic lookup management, FK validation, deactivation locks | 33 | `100% PASS` |
| **Module 4** | `test_purchases_vendors` | Vendor management, Atomic purchase approvals, budget tracking | 23 | `100% PASS` |
| **Module 5** | `test_issue_return.ps1` | Academic loans, Concurrency race conditions, Condition branching | 38 | `100% PASS` |
| **Module 6** | `test_maintenance.ps1` | Repair queues, Damaged return auto-linkage, Status workflow, Cost tracking | 36 | `100% PASS` |
| **Module 7** | `test_depreciation.ps1` | Straight-line depreciation, Dynamic schedules, Non-negative book value floor | 38 | `100% PASS` |
| **Module 8** | `test_inventory_audit.ps1` | Physical audits, VERIFIED/MISSING/MISLOCATED statuses, Audit history filters | 38 | `100% PASS` |
| **Module 9** | `test_user_management.ps1` | Account CRUD, Soft-deactivation locks, Self-lockout prevention, Password resets | 46 | `100% PASS` |
| **Module 10** | `test_dashboard_reports.ps1` | Role dashboards, 7-section report center, Overdue loan alerts, CSV & Print | 68 | `100% PASS` |
| **TOTAL** | **10 Modules End-to-End** | **Full System Regression Verification** | **340 / 340** | **100% GREEN** |

---

## ⚙️ Running Locally

1. **Prerequisites**:
   - Java 17+ installed and on `PATH`.
   - Oracle Database 21c/23c XE running on `localhost:1521/xepdb1`.
2. **Database Credentials**:
   - Verify connection properties in `src/main/resources/db.properties`.
3. **Start the Application Server**:
   ```bash
   mvnw.cmd exec:java -Dexec.mainClass=com.cams.Server
   ```
4. **Access the Application**:
   - System Diagnostics: `http://localhost:8080/index.html`
   - User Login: `http://localhost:8080/pages/login.html`
   - Demo Credentials:
     - Administrator: `admin` / `Admin@123`
     - Faculty: `faculty1` / `Faculty@123`
     - Technical Staff: `tech1` / `Tech@123`

