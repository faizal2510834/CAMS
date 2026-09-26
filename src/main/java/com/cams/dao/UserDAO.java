package com.cams.dao;

import com.cams.model.User;

import java.sql.SQLException;
import java.util.Optional;

/**
 * Data Access Object interface for User entities.
 */
public interface UserDAO {

    /**
     * Finds a user by their username (case-insensitively).
     *
     * @param username the username to look up
     * @return Optional containing the User if found, or empty Optional
     * @throws SQLException on database error
     */
    Optional<User> findByUsername(String username) throws SQLException;

    /**
     * Finds a user by their unique primary key ID.
     *
     * @param userId the user ID
     * @return Optional containing the User if found, or empty Optional
     * @throws SQLException on database error
     */
    Optional<User> findById(Long userId) throws SQLException;

    /**
     * Creates a new user record in the USERS table.
     * The username is guaranteed to be stored in lowercase.
     *
     * @param user the user to persist
     * @return true if inserted successfully
     * @throws SQLException on database error
     */
    boolean createUser(User user) throws SQLException;

    /**
     * Returns total number of registered users.
     *
     * @return count of users
     * @throws SQLException on database error
     */
    long countUsers() throws SQLException;

    /**
     * Finds users matching search and filter criteria with pagination.
     */
    java.util.List<User> findAll(String search, String role, String department, String active, int page, int size) throws SQLException;

    /**
     * Counts users matching search and filter criteria.
     */
    long count(String search, String role, String department, String active) throws SQLException;

    /**
     * Updates editable details (name, role, department) for an existing user.
     */
    boolean updateUser(User user) throws SQLException;

    /**
     * Updates active status (soft-delete / reactivation) for a user.
     */
    boolean setActiveStatus(Long userId, String active) throws SQLException;

    /**
     * Updates password hash and sets must_change_password flag.
     */
    boolean resetPassword(Long userId, String passwordHash, String mustChangePassword) throws SQLException;

    /**
     * Updates must_change_password flag.
     */
    boolean updateMustChangePassword(Long userId, String mustChangePassword) throws SQLException;

    /**
     * Ensures the USERS table exists and seeds the initial 3 demo users if empty.
     *
     * @return true if initialized or verified
     * @throws SQLException on database error
     */
    boolean initSchemaAndSeedIfEmpty() throws SQLException;
}
