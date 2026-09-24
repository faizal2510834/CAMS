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
     * Ensures the USERS table exists and seeds the initial 3 demo users if empty.
     *
     * @return true if initialized or verified
     * @throws SQLException on database error
     */
    boolean initSchemaAndSeedIfEmpty() throws SQLException;
}
