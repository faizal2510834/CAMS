package com.cams.service;

public class MaintenanceNotFoundException extends RuntimeException {
    public MaintenanceNotFoundException(String message) {
        super(message);
    }
}
