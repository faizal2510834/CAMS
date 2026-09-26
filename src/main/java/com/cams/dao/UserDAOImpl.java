package com.cams.dao;

import com.cams.model.User;
import com.cams.util.DBConnection;
import com.cams.util.PasswordUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * JDBC Implementation of UserDAO against Oracle Database.
 * Guarantees case-insensitivity by strictly lowercasing usernames prior to query and persistence.
 * Supports Module 9 User Management operations: search, filter, update, soft-delete, and password reset.
 */
public class UserDAOImpl implements UserDAO {

    private static final Logger LOGGER = Logger.getLogger(UserDAOImpl.class.getName());

    @Override
    public Optional<User> findByUsername(String username) throws SQLException {
        if (username == null || username.trim().isEmpty()) {
            return Optional.empty();
        }

        // Explicitly lowercase before lookup per SRS requirement
        String normalizedUsername = username.trim().toLowerCase();

        String sql = "SELECT USER_ID, NAME, ROLE, DEPARTMENT, USERNAME, PASSWORD, CREATED_AT, ACTIVE, MUST_CHANGE_PASSWORD " +
                     "FROM USERS WHERE USERNAME = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, normalizedUsername);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRowToUser(rs));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<User> findById(Long userId) throws SQLException {
        if (userId == null) {
            return Optional.empty();
        }

        String sql = "SELECT USER_ID, NAME, ROLE, DEPARTMENT, USERNAME, PASSWORD, CREATED_AT, ACTIVE, MUST_CHANGE_PASSWORD " +
                     "FROM USERS WHERE USER_ID = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, userId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRowToUser(rs));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public boolean createUser(User user) throws SQLException {
        if (user == null || user.getUsername() == null || user.getPassword() == null) {
            throw new IllegalArgumentException("User and credentials must not be null");
        }

        // Explicitly lowercase username before storage
        String normalizedUsername = user.getUsername().trim().toLowerCase();
        user.setUsername(normalizedUsername);

        String active = user.getActive() != null ? user.getActive() : "Y";
        String mustChangePassword = user.getMustChangePassword() != null ? user.getMustChangePassword() : "N";

        String sql = "INSERT INTO USERS (NAME, ROLE, DEPARTMENT, USERNAME, PASSWORD, ACTIVE, MUST_CHANGE_PASSWORD) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, new String[]{"USER_ID"})) {

            ps.setString(1, user.getName());
            ps.setString(2, user.getRole());
            ps.setString(3, user.getDepartment());
            ps.setString(4, normalizedUsername);
            ps.setString(5, user.getPassword());
            ps.setString(6, active);
            ps.setString(7, mustChangePassword);

            int affected = ps.executeUpdate();
            if (affected > 0) {
                try (ResultSet generatedKeys = ps.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        user.setUserId(generatedKeys.getLong(1));
                    }
                }
                user.setActive(active);
                user.setMustChangePassword(mustChangePassword);
                return true;
            }
        }
        return false;
    }

    @Override
    public List<User> findAll(String search, String role, String department, String active, int page, int size) throws SQLException {
        List<User> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT USER_ID, NAME, ROLE, DEPARTMENT, USERNAME, PASSWORD, CREATED_AT, ACTIVE, MUST_CHANGE_PASSWORD FROM USERS WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        buildFilterClauses(sql, params, search, role, department, active);

        sql.append("ORDER BY USER_ID DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY");
        int offset = Math.max(0, (page - 1) * size);
        params.add(offset);
        params.add(size > 0 ? size : 20);

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {

            for (int i = 0; i < params.size(); i++) {
                Object p = params.get(i);
                if (p instanceof Integer) {
                    ps.setInt(i + 1, (Integer) p);
                } else {
                    ps.setString(i + 1, (String) p);
                }
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRowToUser(rs));
                }
            }
        }
        return list;
    }

    @Override
    public long count(String search, String role, String department, String active) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM USERS WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        buildFilterClauses(sql, params, search, role, department, active);

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {

            for (int i = 0; i < params.size(); i++) {
                ps.setString(i + 1, (String) params.get(i));
            }

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }
        return 0L;
    }

    private void buildFilterClauses(StringBuilder sql, List<Object> params, String search, String role, String department, String active) {
        if (search != null && !search.trim().isEmpty()) {
            sql.append("AND (LOWER(NAME) LIKE ? OR LOWER(USERNAME) LIKE ?) ");
            String term = "%" + search.trim().toLowerCase() + "%";
            params.add(term);
            params.add(term);
        }
        if (role != null && !role.trim().isEmpty() && !"ALL".equalsIgnoreCase(role.trim())) {
            sql.append("AND ROLE = ? ");
            params.add(role.trim());
        }
        if (department != null && !department.trim().isEmpty() && !"ALL".equalsIgnoreCase(department.trim())) {
            sql.append("AND DEPARTMENT = ? ");
            params.add(department.trim());
        }
        if (active != null && !active.trim().isEmpty() && !"ALL".equalsIgnoreCase(active.trim())) {
            sql.append("AND ACTIVE = ? ");
            params.add(active.trim().toUpperCase());
        }
    }

    @Override
    public boolean updateUser(User user) throws SQLException {
        if (user == null || user.getUserId() == null) {
            return false;
        }

        String sql = "UPDATE USERS SET NAME = ?, ROLE = ?, DEPARTMENT = ? WHERE USER_ID = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, user.getName());
            ps.setString(2, user.getRole());
            ps.setString(3, user.getDepartment());
            ps.setLong(4, user.getUserId());

            return ps.executeUpdate() > 0;
        }
    }

    @Override
    public boolean setActiveStatus(Long userId, String active) throws SQLException {
        if (userId == null || active == null) {
            return false;
        }

        String sql = "UPDATE USERS SET ACTIVE = ? WHERE USER_ID = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, active.trim().toUpperCase());
            ps.setLong(2, userId);

            return ps.executeUpdate() > 0;
        }
    }

    @Override
    public boolean resetPassword(Long userId, String passwordHash, String mustChangePassword) throws SQLException {
        if (userId == null || passwordHash == null) {
            return false;
        }

        String sql = "UPDATE USERS SET PASSWORD = ?, MUST_CHANGE_PASSWORD = ? WHERE USER_ID = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, passwordHash);
            ps.setString(2, mustChangePassword != null ? mustChangePassword.trim().toUpperCase() : "Y");
            ps.setLong(3, userId);

            return ps.executeUpdate() > 0;
        }
    }

    @Override
    public boolean updateMustChangePassword(Long userId, String mustChangePassword) throws SQLException {
        if (userId == null || mustChangePassword == null) {
            return false;
        }

        String sql = "UPDATE USERS SET MUST_CHANGE_PASSWORD = ? WHERE USER_ID = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, mustChangePassword.trim().toUpperCase());
            ps.setLong(2, userId);

            return ps.executeUpdate() > 0;
        }
    }

    @Override
    public long countUsers() throws SQLException {
        String sql = "SELECT COUNT(*) FROM USERS";
        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            if (rs.next()) {
                return rs.getLong(1);
            }
        }
        return 0L;
    }

    @Override
    public boolean initSchemaAndSeedIfEmpty() throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            boolean tableExists = false;
            // Check if USERS table already exists
            try (Statement checkStmt = conn.createStatement();
                 ResultSet rs = checkStmt.executeQuery("SELECT 1 FROM USERS WHERE 1=0")) {
                tableExists = true;
            } catch (SQLException e) {
                tableExists = false;
            }

            if (!tableExists) {
                LOGGER.info("USERS table does not exist. Creating USERS table in Oracle...");
                String createTableSql = "CREATE TABLE USERS (" +
                        "USER_ID NUMBER(10) GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, " +
                        "NAME VARCHAR2(100) NOT NULL, " +
                        "ROLE VARCHAR2(30) NOT NULL CHECK (ROLE IN ('Administrator', 'Faculty', 'Technical Staff')), " +
                        "DEPARTMENT VARCHAR2(100) NOT NULL, " +
                        "USERNAME VARCHAR2(50) NOT NULL UNIQUE, " +
                        "PASSWORD VARCHAR2(255) NOT NULL, " +
                        "CREATED_AT TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                        "ACTIVE VARCHAR2(1) DEFAULT 'Y' NOT NULL CHECK (ACTIVE IN ('Y', 'N')), " +
                        "MUST_CHANGE_PASSWORD VARCHAR2(1) DEFAULT 'N' NOT NULL CHECK (MUST_CHANGE_PASSWORD IN ('Y', 'N'))" +
                        ")";

                try (Statement createStmt = conn.createStatement()) {
                    createStmt.execute(createTableSql);
                    LOGGER.info("USERS table created successfully.");
                } catch (SQLException createEx) {
                    if (!createEx.getMessage().contains("ORA-00955")) {
                        throw createEx;
                    }
                }
            } else {
                // Ensure columns exist on legacy installations
                try (Statement alterStmt = conn.createStatement()) {
                    alterStmt.execute("ALTER TABLE USERS ADD (ACTIVE VARCHAR2(1) DEFAULT 'Y' NOT NULL CHECK (ACTIVE IN ('Y', 'N')))");
                } catch (SQLException ignore) {
                    // Column already exists
                }
                try (Statement alterStmt = conn.createStatement()) {
                    alterStmt.execute("ALTER TABLE USERS ADD (MUST_CHANGE_PASSWORD VARCHAR2(1) DEFAULT 'N' NOT NULL CHECK (MUST_CHANGE_PASSWORD IN ('Y', 'N')))");
                } catch (SQLException ignore) {
                    // Column already exists
                }
            }

            // Check if seed data exists
            long count = 0L;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM USERS")) {
                if (rs.next()) {
                    count = rs.getLong(1);
                }
            }

            if (count == 0L) {
                LOGGER.info("USERS table is empty. Seeding initial test accounts (Admin, Faculty, Technical Staff)...");

                User admin = new User(null, "System Administrator", "Administrator", "IT Infrastructure",
                        "admin", PasswordUtil.hashPassword("Admin@123"), null, "Y", "N");
                User faculty = new User(null, "Dr. Sarah Jenkins", "Faculty", "Computer Science",
                        "faculty1", PasswordUtil.hashPassword("Faculty@123"), null, "Y", "N");
                User faculty2 = new User(null, "Prof. Michael Chang", "Faculty", "Information Technology",
                        "faculty2", PasswordUtil.hashPassword("Faculty@123"), null, "Y", "N");
                User tech = new User(null, "Alex Rivera", "Technical Staff", "Hardware & Maintenance",
                        "tech1", PasswordUtil.hashPassword("Tech@123"), null, "Y", "N");

                createUser(admin);
                createUser(faculty);
                createUser(faculty2);
                createUser(tech);

                LOGGER.info("Seeding completed successfully. Seeded accounts: admin, faculty1, faculty2, tech1");
            } else {
                LOGGER.info("USERS table already populated with " + count + " users.");
            }

            return true;
        }
    }

    private User mapRowToUser(ResultSet rs) throws SQLException {
        User user = new User();
        user.setUserId(rs.getLong("USER_ID"));
        user.setName(rs.getString("NAME"));
        user.setRole(rs.getString("ROLE"));
        user.setDepartment(rs.getString("DEPARTMENT"));
        user.setUsername(rs.getString("USERNAME"));
        user.setPassword(rs.getString("PASSWORD"));
        user.setCreatedAt(rs.getTimestamp("CREATED_AT"));
        try {
            user.setActive(rs.getString("ACTIVE") != null ? rs.getString("ACTIVE") : "Y");
        } catch (SQLException e) {
            user.setActive("Y");
        }
        try {
            user.setMustChangePassword(rs.getString("MUST_CHANGE_PASSWORD") != null ? rs.getString("MUST_CHANGE_PASSWORD") : "N");
        } catch (SQLException e) {
            user.setMustChangePassword("N");
        }
        return user;
    }
}
