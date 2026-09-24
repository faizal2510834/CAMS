package com.cams.service;

/**
 * Exception thrown when an asset operation conflicts with business rules or existing state
 * (e.g. duplicate Asset ID, invalid status transition, editing/retiring an invalid state).
 * Mapped to HTTP 409 Conflict.
 */
public class AssetConflictException extends Exception {

    private static final long serialVersionUID = 1L;

    public AssetConflictException(String message) {
        super(message);
    }
}
