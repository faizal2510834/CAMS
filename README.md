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

### ✅ Module 2: Asset Management
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
  - `changeStatus(Connection con, String assetId, String expectedStatus, String newStatus)` provides optimistic concurrency locking for future transaction modules.
- **Pagination & Whitelisted Sorting**:
  - `GET /api/assets?page=1&size=10` uses Oracle `OFFSET ? ROWS FETCH NEXT ? ROWS ONLY`.
  - Dynamic filtering by keyword, department, category, operational status, and retired inclusion toggle.
  - Whitelisted sort columns prevent SQL injection.
- **UI Screen**:
  - [`/pages/assets.html`](src/main/webapp/pages/assets.html) with role-adaptive interface (mutating controls visible only to Administrator; read-only view for Faculty and Technical Staff).

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
```

---

## 📁 Repository Structure

```
CAMS/
├── .gitignore
├── pom.xml
├── README.md
├── run.bat / run.ps1
├── test_module2.ps1
├── db/
│   └── module2_assets.sql
└── src/main/
    ├── java/com/cams/
    │   ├── Server.java
    │   ├── controller/
    │   │   ├── AssetServlet.java
    │   │   ├── AuthServlet.java
    │   │   ├── PingServlet.java
    │   │   └── RoleTestServlet.java
    │   ├── dao/
    │   │   ├── AssetDAO.java / AssetDAOImpl.java
    │   │   ├── PingDAO.java / PingDAOImpl.java
    │   │   └── UserDAO.java / UserDAOImpl.java
    │   ├── filter/
    │   │   ├── AuthenticationFilter.java
    │   │   └── AuthorizationFilter.java
    │   ├── model/
    │   │   ├── Asset.java
    │   │   ├── AssetQueryCriteria.java
    │   │   ├── PagedResult.java
    │   │   ├── PingResult.java
    │   │   └── User.java
    │   ├── service/
    │   │   ├── AssetService.java / AssetServiceImpl.java
    │   │   ├── AuthService.java / AuthServiceImpl.java
    │   │   └── PingService.java
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
        │   └── assets.js
        ├── pages/
        │   ├── access-denied.html
        │   ├── assets.html
        │   ├── login.html
        │   ├── admin/dashboard.html
        │   ├── faculty/dashboard.html
        │   └── technical/dashboard.html
        └── WEB-INF/web.xml
```
