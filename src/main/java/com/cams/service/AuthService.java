package com.cams.service;

import com.cams.model.User;

import java.sql.SQLException;

/**
 * Service interface handling user authentication and security lifecycle operations.
 */
public interface AuthService {

    /**
     * Authenticates credentials against the database.
     * The username is explicitly normalized to lowercase before validation.
     *
     * @param username plain-text username entered by client
     * @param password plain-text password entered by client
     * @return safe User object (password stripped) on success
     * @throws AuthenticationException if username not found or password hash does not match
     * @throws SQLException on database connectivity failure
     */
    User authenticate(String username, String password) throws AuthenticationException, SQLException;

    /**
     * Retrieves safe user profile by ID.
     *
     * @param userId user primary key
     * @return safe User object, or null if not found
     * @throws SQLException on database failure
     */
    User getUserById(Long userId) throws SQLException;

    /**
     * Returns the appropriate dashboard routing URL based on the user's role.
     *
     * @param role role name
     * @return relative dashboard path
     */
    String getDashboardUrlForRole(String role);

    /**
     * Ensures table schema and default seeds exist in the database.
     *
     * @throws SQLException on database error
     */
    void ensureSchemaAndSeed() throws SQLException;
}
