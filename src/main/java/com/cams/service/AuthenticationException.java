package com.cams.service;

/**
 * Exception thrown when authentication fails due to invalid credentials,
 * deactivated account, or missing mandatory identity data.
 */
public class AuthenticationException extends Exception {

    private static final long serialVersionUID = 1L;

    public AuthenticationException(String message) {
        super(message);
    }

    public AuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
}
