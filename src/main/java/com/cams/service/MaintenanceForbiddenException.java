package com.cams.service;

public class MaintenanceForbiddenException extends RuntimeException {
    public MaintenanceForbiddenException(String message) {
        super(message);
    }
}
