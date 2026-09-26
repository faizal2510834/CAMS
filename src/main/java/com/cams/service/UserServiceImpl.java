package com.cams.service;

import com.cams.dao.UserDAO;
import com.cams.dao.UserDAOImpl;
import com.cams.model.User;
import com.cams.util.PasswordUtil;

import java.security.SecureRandom;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Implementation of UserService for Module 9: User Management.
 */
public class UserServiceImpl implements UserService {

    private static final Logger LOGGER = Logger.getLogger(UserServiceImpl.class.getName());
    private static final List<String> VALID_ROLES = Arrays.asList("Administrator", "Faculty", "Technical Staff");
    private static final String TEMP_PASS_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserDAO userDAO;

    public UserServiceImpl() {
        this(new UserDAOImpl());
    }

    public UserServiceImpl(UserDAO userDAO) {
        this.userDAO = userDAO;
    }

    @Override
    public List<User> getUsers(String search, String role, String department, String active, int page, int size) throws SQLException {
        int validPage = page > 0 ? page : 1;
        int validSize = size > 0 ? size : 20;
        List<User> users = userDAO.findAll(search, role, department, active, validPage, validSize);
        return users.stream().map(User::toSafeUser).toList();
    }

    @Override
    public long getTotalUsers(String search, String role, String department, String active) throws SQLException {
        return userDAO.count(search, role, department, active);
    }

    @Override
    public User getUserById(Long userId) throws UserNotFoundException, SQLException {
        if (userId == null) {
            throw new UserValidationException("User ID is required");
        }
        Optional<User> userOpt = userDAO.findById(userId);
        if (userOpt.isEmpty()) {
            throw new UserNotFoundException("User not found with ID: " + userId);
        }
        return userOpt.get().toSafeUser();
    }

    @Override
    public User createUser(User user, String rawPassword) throws UserValidationException, UserConflictException, SQLException {
        if (user == null) {
            throw new UserValidationException("User payload must not be null");
        }

        validateName(user.getName());
        validateRole(user.getRole());
        validateDepartment(user.getDepartment());

        if (user.getUsername() == null || user.getUsername().trim().isEmpty()) {
            throw new UserValidationException("Username is required");
        }
        String normalizedUsername = user.getUsername().trim().toLowerCase();
        if (normalizedUsername.length() < 3 || normalizedUsername.length() > 50) {
            throw new UserValidationException("Username must be between 3 and 50 characters");
        }
        if (!normalizedUsername.matches("^[a-z0-9_.]+$")) {
            throw new UserValidationException("Username can only contain alphanumeric characters, underscores, and dots");
        }

        if (rawPassword == null || rawPassword.trim().length() < 6) {
            throw new UserValidationException("Password must be at least 6 characters long");
        }

        // Check duplicate username (case-insensitive)
        if (userDAO.findByUsername(normalizedUsername).isPresent()) {
            throw new UserConflictException("Username '" + normalizedUsername + "' is already registered");
        }

        user.setUsername(normalizedUsername);
        user.setPassword(PasswordUtil.hashPassword(rawPassword.trim()));
        user.setActive("Y");
        user.setMustChangePassword("N");

        boolean created = userDAO.createUser(user);
        if (!created) {
            throw new SQLException("Failed to insert user record into database");
        }

        LOGGER.info("Created user: " + normalizedUsername + " with ID: " + user.getUserId() + " (" + user.getRole() + ")");
        return user.toSafeUser();
    }

    @Override
    public User updateUser(Long userId, User updatedData) throws UserValidationException, UserNotFoundException, SQLException {
        if (userId == null) {
            throw new UserValidationException("User ID is required for update");
        }
        if (updatedData == null) {
            throw new UserValidationException("Update data cannot be null");
        }

        validateName(updatedData.getName());
        validateRole(updatedData.getRole());
        validateDepartment(updatedData.getDepartment());

        Optional<User> existingOpt = userDAO.findById(userId);
        if (existingOpt.isEmpty()) {
            throw new UserNotFoundException("User not found with ID: " + userId);
        }

        User existing = existingOpt.get();
        existing.setName(updatedData.getName().trim());
        existing.setRole(normalizeRole(updatedData.getRole()));
        existing.setDepartment(updatedData.getDepartment().trim());

        boolean updated = userDAO.updateUser(existing);
        if (!updated) {
            throw new SQLException("Failed to update user ID: " + userId);
        }

        LOGGER.info("Updated user ID: " + userId + " (" + existing.getUsername() + ")");
        return existing.toSafeUser();
    }

    @Override
    public boolean deactivateUser(Long targetUserId, Long currentAdminId) throws UserValidationException, UserConflictException, UserNotFoundException, SQLException {
        if (targetUserId == null) {
            throw new UserValidationException("Target user ID is required");
        }

        // Self-deactivation prevention rule
        if (targetUserId.equals(currentAdminId)) {
            throw new UserConflictException("Administrators cannot deactivate their own account");
        }

        Optional<User> targetOpt = userDAO.findById(targetUserId);
        if (targetOpt.isEmpty()) {
            throw new UserNotFoundException("User not found with ID: " + targetUserId);
        }

        boolean success = userDAO.setActiveStatus(targetUserId, "N");
        if (success) {
            LOGGER.info("Deactivated user ID: " + targetUserId + " by Admin ID: " + currentAdminId);
        }
        return success;
    }

    @Override
    public boolean activateUser(Long targetUserId) throws UserNotFoundException, SQLException {
        if (targetUserId == null) {
            throw new UserValidationException("Target user ID is required");
        }

        Optional<User> targetOpt = userDAO.findById(targetUserId);
        if (targetOpt.isEmpty()) {
            throw new UserNotFoundException("User not found with ID: " + targetUserId);
        }

        boolean success = userDAO.setActiveStatus(targetUserId, "Y");
        if (success) {
            LOGGER.info("Reactivated user ID: " + targetUserId);
        }
        return success;
    }

    @Override
    public String resetPassword(Long targetUserId, String customPassword) throws UserValidationException, UserNotFoundException, SQLException {
        if (targetUserId == null) {
            throw new UserValidationException("Target user ID is required");
        }

        Optional<User> targetOpt = userDAO.findById(targetUserId);
        if (targetOpt.isEmpty()) {
            throw new UserNotFoundException("User not found with ID: " + targetUserId);
        }

        String tempPassword;
        if (customPassword != null && !customPassword.trim().isEmpty()) {
            if (customPassword.trim().length() < 6) {
                throw new UserValidationException("Temporary password must be at least 6 characters long");
            }
            tempPassword = customPassword.trim();
        } else {
            tempPassword = generateSecureTempPassword();
        }

        String passwordHash = PasswordUtil.hashPassword(tempPassword);
        // Force must_change_password = 'Y'
        boolean success = userDAO.resetPassword(targetUserId, passwordHash, "Y");
        if (!success) {
            throw new SQLException("Failed to reset password for user ID: " + targetUserId);
        }

        LOGGER.info("Password reset executed for user ID: " + targetUserId + "; mustChangePassword set to 'Y'");
        return tempPassword;
    }

    private void validateName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new UserValidationException("Full name is required");
        }
        if (name.trim().length() > 100) {
            throw new UserValidationException("Name cannot exceed 100 characters");
        }
    }

    private void validateDepartment(String department) {
        if (department == null || department.trim().isEmpty()) {
            throw new UserValidationException("Department is required");
        }
        if (department.trim().length() > 100) {
            throw new UserValidationException("Department cannot exceed 100 characters");
        }
    }

    private void validateRole(String role) {
        if (role == null || role.trim().isEmpty()) {
            throw new UserValidationException("Role is required");
        }
        String normalized = normalizeRole(role);
        if (!VALID_ROLES.contains(normalized)) {
            throw new UserValidationException("Invalid role '" + role + "'. Must be one of: Administrator, Faculty, Technical Staff");
        }
    }

    private String normalizeRole(String role) {
        if (role == null) return null;
        for (String valid : VALID_ROLES) {
            if (valid.equalsIgnoreCase(role.trim())) {
                return valid;
            }
        }
        return role.trim();
    }

    private String generateSecureTempPassword() {
        StringBuilder sb = new StringBuilder("Temp@");
        for (int i = 0; i < 6; i++) {
            sb.append(TEMP_PASS_CHARS.charAt(RANDOM.nextInt(TEMP_PASS_CHARS.length())));
        }
        return sb.toString();
    }
}
