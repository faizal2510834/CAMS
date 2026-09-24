package com.cams.service;

/**
 * Exception thrown when asset input validation fails (e.g. cost <= 0, invalid regex).
 * Mapped to HTTP 400 Bad Request.
 */
public class AssetValidationException extends Exception {

    private static final long serialVersionUID = 1L;

    public AssetValidationException(String message) {
        super(message);
    }
}
