package com.cams.service;

/**
 * Exception thrown when a requested asset is not found.
 * Mapped to HTTP 404 Not Found.
 */
public class AssetNotFoundException extends Exception {

    private static final long serialVersionUID = 1L;

    public AssetNotFoundException(String message) {
        super(message);
    }
}
