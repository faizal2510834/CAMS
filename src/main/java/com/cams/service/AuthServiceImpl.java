package com.cams.service;

import com.cams.dao.UserDAO;
import com.cams.dao.UserDAOImpl;
import com.cams.model.User;
import com.cams.util.PasswordUtil;

import java.sql.SQLException;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Business logic implementation for Authentication and Role routing.
 */
public class AuthServiceImpl implements AuthService {

    private static final Logger LOGGER = Logger.getLogger(AuthServiceImpl.class.getName());

    private final UserDAO userDAO;

    public AuthServiceImpl() {
        this(new UserDAOImpl());
    }

    public AuthServiceImpl(UserDAO userDAO) {
        this.userDAO = userDAO;
    }

    @Override
    public User authenticate(String username, String password) throws AuthenticationException, SQLException {
        if (username == null || username.trim().isEmpty() || password == null || password.isEmpty()) {
            throw new AuthenticationException("Invalid username or password");
        }

        // Explicitly lowercase username before querying per SRS requirement
        String normalizedUsername = username.trim().toLowerCase();

        Optional<User> userOpt = userDAO.findByUsername(normalizedUsername);
        if (userOpt.isEmpty()) {
            LOGGER.warning("Authentication failed: Username not found -> " + normalizedUsername);
            throw new AuthenticationException("Invalid username or password");
        }

        User user = userOpt.get();
        boolean valid = PasswordUtil.verifyPassword(password, user.getPassword());
        if (!valid) {
            LOGGER.warning("Authentication failed: Password mismatch for user -> " + normalizedUsername);
            throw new AuthenticationException("Invalid username or password");
        }

        LOGGER.info("Authentication successful for user: " + normalizedUsername + " (" + user.getRole() + ")");
        return user.toSafeUser();
    }

    @Override
    public User getUserById(Long userId) throws SQLException {
        if (userId == null) {
            return null;
        }
        return userDAO.findById(userId).map(User::toSafeUser).orElse(null);
    }

    @Override
    public String getDashboardUrlForRole(String role) {
        if (role == null) {
            return "/pages/login.html";
        }

        return switch (role.trim()) {
            case "Administrator" -> "/pages/admin/dashboard.html";
            case "Faculty" -> "/pages/faculty/dashboard.html";
            case "Technical Staff" -> "/pages/technical/dashboard.html";
            default -> "/pages/login.html";
        };
    }

    @Override
    public void ensureSchemaAndSeed() throws SQLException {
        userDAO.initSchemaAndSeedIfEmpty();
    }
}
