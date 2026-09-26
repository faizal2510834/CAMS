package com.cams.service;

import com.cams.model.User;

import java.sql.SQLException;
import java.util.List;

/**
 * Service interface for Module 9: User Management.
 * Provides Admin operations: search, detail lookup, creation, modification,
 * soft-deactivation (with self-deactivation protection), reactivation, and password reset.
 */
public interface UserService {

    List<User> getUsers(String search, String role, String department, String active, int page, int size) throws SQLException;

    long getTotalUsers(String search, String role, String department, String active) throws SQLException;

    User getUserById(Long userId) throws UserNotFoundException, SQLException;

    User createUser(User user, String rawPassword) throws UserValidationException, UserConflictException, SQLException;

    User updateUser(Long userId, User updatedData) throws UserValidationException, UserNotFoundException, SQLException;

    boolean deactivateUser(Long targetUserId, Long currentAdminId) throws UserValidationException, UserConflictException, UserNotFoundException, SQLException;

    boolean activateUser(Long targetUserId) throws UserNotFoundException, SQLException;

    String resetPassword(Long targetUserId, String customPassword) throws UserValidationException, UserNotFoundException, SQLException;
}
